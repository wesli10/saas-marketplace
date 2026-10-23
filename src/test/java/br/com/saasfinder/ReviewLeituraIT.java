package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.domain.Review;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Criterio de verificacao da tarefa 003-T08: AC-10 a AC-13a. */
class ReviewLeituraIT extends AbstractIntegrationTest {

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

    private UUID acmeChat;

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
        acmeChat = produtos.save(produto).getId();
    }

    /** Cria uma review ja no estado pedido, para nao depender de HTTP em teste de leitura. */
    private Review criar(int nota, boolean recomenda, String locale, String email, boolean gratuito, boolean publicar) {
        Review review = new Review(acmeChat, nota, recomenda, "Titulo", CORPO, locale, "Ana Ribeiro", email);
        if (publicar) {
            review.confirmar(gratuito);
            review.publicar("curador-teste");
        }
        return reviews.saveAndFlush(review);
    }

    private JsonNode listar() {
        return http.getForObject("/api/v1/products/acme-chat/reviews", JsonNode.class);
    }

    @Test
    @DisplayName("produto sem review publicada devolve rating null, e nao zero (AC-12)")
    void semReviewOAgregadoENulo() {
        criar(5, true, "pt-BR", "pendente@acme.com.br", false, false);

        JsonNode corpo = listar();

        assertThat(corpo.get("rating").isNull()).isTrue();
        assertThat(corpo.get("items")).isEmpty();
        assertThat(corpo.get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("so as publicadas aparecem, da mais recente para a mais antiga (AC-11)")
    void soPublicadasEMaisRecentePrimeiro() {
        Review antiga = criar(4, true, "pt-BR", "antiga@acme.com.br", false, true);
        criar(1, false, "pt-BR", "pendente@acme.com.br", false, false);
        Review recente = criar(5, true, "pt-BR", "recente@acme.com.br", false, true);

        JsonNode corpo = listar();

        List<String> ids = corpo.get("items").findValuesAsText("id");
        assertThat(ids)
                .containsExactly(recente.getId().toString(), antiga.getId().toString());
        assertThat(corpo.get("total").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("a media tem uma casa decimal e sai dos dois idiomas (AC-12, AC-13)")
    void mediaComUmaCasaEDosDoisIdiomas() {
        criar(4, true, "pt-BR", "ana@acme.com.br", false, true);
        criar(5, true, "en", "john@acme.com", false, true);
        criar(4, false, "en", "mary@acme.com", false, true);

        JsonNode rating = listar().get("rating");

        // (4 + 5 + 4) / 3 = 4,333... -> 4,3
        assertThat(rating.get("average").asDouble()).isEqualTo(4.3);
        assertThat(rating.get("count").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("o agregado traz a proporcao de recomendaria e a contagem de verificadas (AC-13a)")
    void agregadoTrazOsInsumosDoRanking() {
        criar(5, true, "pt-BR", "ana@acme.com.br", false, true);
        criar(4, true, "pt-BR", "bia@acme.com.br", false, true);
        criar(2, false, "pt-BR", "carlos@gmail.com", true, true);

        JsonNode rating = listar().get("rating");

        assertThat(rating.get("count").asLong()).isEqualTo(3);
        // 2 de 3 recomendariam, arredondado em duas casas.
        assertThat(rating.get("recommend_ratio").asDouble()).isEqualTo(0.67);
        // A do gmail confirmou, mas nao tem selo (spec secao 4.5).
        assertThat(rating.get("verified_count").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("o agregado e de todas as publicadas, e nao so das que couberam na pagina")
    void agregadoNaoDependeDaPagina() {
        for (int i = 0; i < 3; i++) {
            criar(5, true, "pt-BR", "pessoa%d@acme.com.br".formatted(i), false, true);
        }

        JsonNode pagina = http.getForObject("/api/v1/products/acme-chat/reviews?size=1", JsonNode.class);

        assertThat(pagina.get("items")).hasSize(1);
        assertThat(pagina.get("total").asLong()).isEqualTo(3);
        assertThat(pagina.get("rating").get("count").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("nenhum campo de moderacao, de e-mail ou de verificacao sai no corpo (AC-10)")
    void corpoPublicoNaoVazaNada() {
        Review review = criar(4, true, "pt-BR", "ana.ribeiro@acme.com.br", false, true);
        // Uma decisao de moderacao registrada, para o campo existir no banco.
        jdbc.update("update review set moderation_note = 'contestacao analisada' where id = ?", review.getId());
        jdbc.update(
                "insert into review_verification (review_id, token_hash, expires_at) values (?, ?, now())",
                review.getId(),
                "a".repeat(64));

        String corpo = listar().toString();

        // Conferido no JSON servido, e nao no DTO: e o corpo que chega ao visitante.
        assertThat(corpo)
                .doesNotContain("ana.ribeiro@acme.com.br")
                .doesNotContain("author_email")
                .doesNotContain("moderation_note")
                .doesNotContain("contestacao analisada")
                .doesNotContain("moderated_by")
                .doesNotContain("token_hash")
                .doesNotContain(review.getAuthorEmail());

        // E o que precisa aparecer, aparece.
        JsonNode item = listar().get("items").get(0);
        assertThat(item.get("author_name").asText()).isEqualTo("Ana Ribeiro");
        assertThat(item.get("author_domain").asText()).isEqualTo("acme.com.br");
        assertThat(item.get("locale").asText()).isEqualTo("pt-BR");
        assertThat(item.get("verified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("slug desconhecido responde 404")
    void slugDesconhecido() {
        assertThat(http.getForEntity("/api/v1/products/nao-existe/reviews", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
