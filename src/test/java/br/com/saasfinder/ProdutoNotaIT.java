package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.domain.Review;
import br.com.saasfinder.reviews.infra.ReviewAggregateJdbc;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Criterio de verificacao da tarefa 003-T09: AC-14, o N+1 que nao pode existir.
 *
 * <p>A contagem e feita sobre o {@link ReviewAggregateJdbc}, e nao sobre o total de
 * SQL do build. E onde a afirmacao tem sentido: aquele componente emite
 * <b>exatamente uma</b> consulta por chamada - o {@code group by} do plano DT-06 -,
 * entao "uma chamada com os 20 ids" e "uma consulta de nota para a pagina". Contar
 * SQL bruto exigiria um proxy de DataSource so para o teste, e mediria junto tudo o
 * que a listagem ja fazia antes desta fatia.
 */
class ProdutoNotaIT extends AbstractIntegrationTest {

    private static final String CORPO =
            "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade.";

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

    @MockitoSpyBean
    private ReviewAggregateJdbc agregados;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        Category categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));

        for (int i = 0; i < 20; i++) {
            produtos.save(publicado("produto-%02d".formatted(i), "Produto %02d".formatted(i), categoria));
        }
        clearInvocations(agregados);
    }

    private Product publicado(String slug, String nome, Category categoria) {
        Product produto = new Product(
                slug,
                nome,
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
        return produto;
    }

    private void publicarReview(String slug, int nota, String email) {
        UUID produto = produtos.buscarPublicadoPorSlug(slug).orElseThrow().getId();
        Review review = new Review(produto, nota, true, "Titulo", CORPO, "pt-BR", "Ana Ribeiro", email);
        review.confirmar(false);
        review.publicar("curador-teste");
        reviews.saveAndFlush(review);
    }

    @Test
    @DisplayName("uma listagem de 20 produtos faz UMA consulta de nota, com os 20 ids (AC-14)")
    void listagemFazUmaConsultaDeNota() {
        publicarReview("produto-00", 5, "ana@acme.com.br");
        publicarReview("produto-01", 3, "bia@acme.com.br");
        clearInvocations(agregados);

        JsonNode corpo = http.getForObject("/api/v1/products?size=20", JsonNode.class);
        assertThat(corpo.get("items")).hasSize(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> pedidos = ArgumentCaptor.forClass(Collection.class);
        verify(agregados).notasDe(pedidos.capture());

        // Uma chamada so, e com a pagina inteira dentro dela.
        assertThat(pedidos.getAllValues()).hasSize(1);
        assertThat(pedidos.getValue()).hasSize(20);
    }

    @Test
    @DisplayName("a nota aparece no card, e produto sem review vem com rating null")
    void notaNoCard() {
        publicarReview("produto-00", 5, "ana@acme.com.br");
        publicarReview("produto-00", 4, "bia@acme.com.br");

        JsonNode corpo = http.getForObject("/api/v1/products?size=20", JsonNode.class);

        JsonNode comNota = item(corpo, "produto-00");
        assertThat(comNota.get("rating").get("average").asDouble()).isEqualTo(4.5);
        assertThat(comNota.get("rating").get("count").asLong()).isEqualTo(2);

        JsonNode semNota = item(corpo, "produto-01");
        assertThat(semNota.get("rating").isNull()).isTrue();
    }

    @Test
    @DisplayName("o id do produto continua fora do JSON: a API publica fala por slug")
    void idNaoVazaNoCard() {
        JsonNode corpo = http.getForObject("/api/v1/products?size=20", JsonNode.class);

        assertThat(corpo.get("items").get(0).has("id")).isFalse();
    }

    @Test
    @DisplayName("a ficha traz a nota, e faz uma consulta de nota so")
    void notaNaFicha() {
        publicarReview("produto-00", 4, "ana@acme.com.br");
        clearInvocations(agregados);

        JsonNode ficha = http.getForObject("/api/v1/products/produto-00", JsonNode.class);

        assertThat(ficha.get("rating").get("average").asDouble()).isEqualTo(4.0);
        assertThat(ficha.get("rating").get("recommend_ratio").asDouble()).isEqualTo(1.0);
        verify(agregados).notasDe(any());
    }

    @Test
    @DisplayName("a ficha de produto sem review traz rating null, e nao zero")
    void fichaSemReview() {
        JsonNode ficha = http.getForObject("/api/v1/products/produto-05", JsonNode.class);

        assertThat(ficha.has("rating")).isTrue();
        assertThat(ficha.get("rating").isNull()).isTrue();
    }

    @Test
    @DisplayName("a comparacao traz as notas das tres fichas numa consulta so")
    void notaNaComparacao() {
        publicarReview("produto-00", 5, "ana@acme.com.br");
        publicarReview("produto-01", 2, "bia@acme.com.br");
        clearInvocations(agregados);

        JsonNode comparacao =
                http.getForObject("/api/v1/products/compare?slugs=produto-00,produto-01,produto-02", JsonNode.class);

        assertThat(comparacao.get("items").get(0).get("rating").get("average").asDouble())
                .isEqualTo(5.0);
        assertThat(comparacao.get("items").get(1).get("rating").get("average").asDouble())
                .isEqualTo(2.0);
        assertThat(comparacao.get("items").get(2).get("rating").isNull()).isTrue();

        verify(agregados).notasDe(any());
    }

    private static JsonNode item(JsonNode lista, String slug) {
        for (JsonNode item : lista.get("items")) {
            if (slug.equals(item.get("slug").asText())) {
                return item;
            }
        }
        throw new AssertionError("produto %s nao veio na lista".formatted(slug));
    }
}
