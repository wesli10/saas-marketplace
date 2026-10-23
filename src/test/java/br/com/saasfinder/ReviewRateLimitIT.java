package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Criterio de verificacao da tarefa 003-T06, com os limites de producao (spec
 * Q-01): 1 por minuto e 5 por hora, e sem confiar em {@code X-Forwarded-For}.
 *
 * <p>Contexto proprio, porque a suite compartilhada roda com limites frouxos de
 * proposito (ver {@code AbstractIntegrationTest}).
 *
 * <p><b>Um metodo de teste so</b>, e nao um por asserção: o limitador guarda
 * estado por IP, e daqui todas as chamadas saem de 127.0.0.1. Um segundo metodo
 * comecaria com o limite ja gasto pelo primeiro, e o que ele afirmasse dependeria
 * da ordem de execucao - exatamente o que a constituicao secao 6 proibe.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "ADMIN_USER=curador-teste",
            "ADMIN_PASSWORD=senha-de-teste",
            "RATE_LIMIT_POR_MINUTO=1",
            "RATE_LIMIT_POR_HORA=5",
            "TRUST_FORWARDED_FOR=false"
        })
class ReviewRateLimitIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EnviadorDeEmail enviador;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        Category categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));

        Product produto = new Product(
                "acme-chat",
                "Acme Chat",
                categoria,
                "atendimento no whatsapp",
                "whatsapp support",
                "Plataforma brasileira de atendimento.",
                "Brazilian customer support platform.",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing - 2026-08-27");
        produto.setStatus(ProductStatus.PUBLISHED);
        produto.setPublishedAt(Instant.now());
        produtos.save(produto);
    }

    @Test
    @DisplayName("o segundo envio no mesmo minuto responde 429 com Retry-After, e o header nao contorna")
    void limitePorMinuto() {
        ResponseEntity<String> primeira = enviar(null);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> segunda = enviar(null);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(segunda.getHeaders().getContentType()).hasToString(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        // Retry-After presente, positivo e dentro da janela de um minuto.
        long espera = Long.parseLong(segunda.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertThat(espera).isBetween(1L, 60L);

        // Forjar o header nao cria um IP novo: sem proxy configurado, ele e ignorado.
        assertThat(enviar("203.0.113.7").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // O reenvio divide o mesmo limite - senao seria a porta de servico do envio.
        assertThat(reenviar().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Leitura nao e limitada: o catalogo nao tem o problema que o limite resolve.
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // E, apesar das quatro recusas, so a primeira review existe.
        assertThat(reviews.count()).isEqualTo(1);
    }

    private ResponseEntity<String> enviar(String ipForjado) {
        String corpo =
                """
                {
                  "rating": 4,
                  "would_recommend": true,
                  "title": "Resolveu o atendimento",
                  "body": "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade.",
                  "author_name": "Ana Ribeiro",
                  "author_email": "%s@acme.com.br"
                }
                """
                        .formatted(UUID.randomUUID());

        return http.postForEntity("/api/v1/products/acme-chat/reviews", comJson(corpo, ipForjado), String.class);
    }

    private ResponseEntity<String> reenviar() {
        return http.postForEntity(
                "/api/v1/reviews/resend",
                comJson("{\"product_slug\": \"acme-chat\", \"email\": \"ana@acme.com.br\"}", null),
                String.class);
    }

    private HttpEntity<String> comJson(String corpo, String ipForjado) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (ipForjado != null) {
            cabecalhos.set("X-Forwarded-For", ipForjado);
        }
        return new HttpEntity<>(corpo, cabecalhos);
    }
}
