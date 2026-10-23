package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
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
 * A outra metade da tarefa 003-T06: com proxy declarado, o IP vem do
 * {@code X-Forwarded-For} e cada cliente tem a propria janela.
 *
 * <p>Cada metodo usa enderecos proprios, porque o limitador guarda estado entre
 * eles - dois metodos com o mesmo IP dependeriam da ordem de execucao.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "ADMIN_USER=curador-teste",
            "ADMIN_PASSWORD=senha-de-teste",
            "RATE_LIMIT_POR_MINUTO=1",
            "RATE_LIMIT_POR_HORA=5",
            "TRUST_FORWARDED_FOR=true"
        })
class ReviewRateLimitProxyIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

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
    @DisplayName("IPs diferentes nao dividem o limite")
    void ipsDiferentesNaoSeAfetam() {
        assertThat(enviar("198.51.100.1").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(enviar("198.51.100.2").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(enviar("198.51.100.3").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("o mesmo IP encaminhado esbarra no limite, e a lista de proxies nao confunde")
    void mesmoIpEncaminhadoEBarrado() {
        assertThat(enviar("203.0.113.10").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Mesmo cliente, agora com a cadeia de proxies que um balanceador
        // acrescenta: o primeiro da lista continua sendo quem manda.
        assertThat(enviar("203.0.113.10, 10.0.0.9").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<String> enviar(String forwardedFor) {
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

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Forwarded-For", forwardedFor);

        return http.postForEntity(
                "/api/v1/products/acme-chat/reviews", new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
