package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.Email;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.domain.Review;
import br.com.saasfinder.reviews.domain.ReviewStatus;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Criterio de verificacao da tarefa 003-T07: AC-08, AC-09 e AC-21c. */
class AdminReviewIT extends AbstractIntegrationTest {

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
    private UUID acmeCrm;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        Category categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        acmeChat = produtos.save(publicado("acme-chat", "Acme Chat", categoria)).getId();
        acmeCrm = produtos.save(publicado("acme-crm", "Acme CRM", categoria)).getId();
        clearInvocations(enviador);
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

    /** Cria direto pelo repositorio: o caminho do POST ja e coberto pela T04. */
    private Review criar(UUID produto, String email, String locale, boolean confirmada) {
        Review review = new Review(produto, 4, true, "Resolveu o atendimento", CORPO, locale, "Ana Ribeiro", email);
        if (confirmada) {
            review.confirmar(false);
        }
        return reviews.saveAndFlush(review);
    }

    private TestRestTemplate comoCurador() {
        return http.withBasicAuth("curador-teste", "senha-de-teste");
    }

    private HttpEntity<String> motivo(String texto) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>("{\"motivo\": \"%s\"}".formatted(texto), cabecalhos);
    }

    @Test
    @DisplayName("sem credencial de curadoria, tudo responde 401 (AC-09)")
    void semCredencial() {
        Review review = criar(acmeChat, "ana@acme.com.br", "pt-BR", true);

        assertThat(http.getForEntity("/api/v1/admin/reviews", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/v1/admin/reviews/" + review.getId() + "/approve", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity(
                                "/api/v1/admin/reviews/" + review.getId() + "/reject", motivo("qualquer"), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewStatus.PENDING_MODERATION);
    }

    @Test
    @DisplayName("aprovar publica, grava quem e quando, e avisa o autor no idioma da review (AC-08, AC-21c)")
    void aprovarPublicaEAvisa() {
        Review review = criar(acmeChat, "john@acme.com", "en", true);

        ResponseEntity<JsonNode> resposta = comoCurador()
                .postForEntity("/api/v1/admin/reviews/" + review.getId() + "/approve", null, JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("status").asText()).isEqualTo("PUBLISHED");

        Review publicada = reviews.findById(review.getId()).orElseThrow();
        assertThat(publicada.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(publicada.getPublishedAt()).isNotNull();
        assertThat(publicada.getModeratedBy()).isEqualTo("curador-teste");
        assertThat(publicada.getModeratedAt()).isNotNull();

        ArgumentCaptor<Email> enviado = ArgumentCaptor.forClass(Email.class);
        verify(enviador).enviar(enviado.capture());
        assertThat(enviado.getValue().destinatario()).isEqualTo("john@acme.com");
        assertThat(enviado.getValue().assunto()).isEqualTo("Your review of Acme Chat is live");
        assertThat(enviado.getValue().corpo()).contains("/en/products/acme-chat");
    }

    @Test
    @DisplayName("aprovar review que nao confirmou o e-mail responde 409 (AC-08)")
    void aprovarNaoConfirmada() {
        Review review = criar(acmeChat, "ana@acme.com.br", "pt-BR", false);

        ResponseEntity<JsonNode> resposta = comoCurador()
                .postForEntity("/api/v1/admin/reviews/" + review.getId() + "/approve", null, JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getHeaders().getContentType()).hasToString(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(reviews.findById(review.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        verify(enviador, never()).enviar(any(Email.class));
    }

    @Test
    @DisplayName("rejeitar sem motivo responde 400; com motivo, grava a decisao e nao manda e-mail (AC-08, Q-02)")
    void rejeitar() {
        Review review = criar(acmeChat, "ana@acme.com.br", "pt-BR", true);
        String rota = "/api/v1/admin/reviews/" + review.getId() + "/reject";

        assertThat(comoCurador()
                        .postForEntity(rota, motivo("   "), JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reviews.findById(review.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewStatus.PENDING_MODERATION);

        assertThat(comoCurador()
                        .postForEntity(rota, motivo("texto sem relacao com o produto"), JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Review rejeitada = reviews.findById(review.getId()).orElseThrow();
        assertThat(rejeitada.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(rejeitada.getModerationNote()).isEqualTo("texto sem relacao com o produto");
        assertThat(rejeitada.getModeratedBy()).isEqualTo("curador-teste");
        assertThat(rejeitada.getModeratedAt()).isNotNull();

        // Nada de e-mail de rejeicao (spec Q-02).
        verify(enviador, never()).enviar(any(Email.class));
    }

    @Test
    @DisplayName("a decisao de manter grava motivo, quem e quando, sem mudar o estado (AC-21c)")
    void decisaoDeManter() {
        Review review = criar(acmeChat, "ana@acme.com.br", "pt-BR", true);
        comoCurador().postForEntity("/api/v1/admin/reviews/" + review.getId() + "/approve", null, JsonNode.class);

        ResponseEntity<JsonNode> resposta = comoCurador()
                .postForEntity(
                        "/api/v1/admin/reviews/" + review.getId() + "/note",
                        motivo("contestacao analisada: a critica e sobre o produto, nao viola a politica"),
                        JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        Review mantida = reviews.findById(review.getId()).orElseThrow();
        assertThat(mantida.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(mantida.getModerationNote()).startsWith("contestacao analisada");
        assertThat(mantida.getModeratedBy()).isEqualTo("curador-teste");
        assertThat(mantida.getModeratedAt()).isNotNull();
    }

    @Test
    @DisplayName("registrar decisao de manter sobre review nao publicada responde 409")
    void manterExigePublicada() {
        Review review = criar(acmeChat, "ana@acme.com.br", "pt-BR", true);

        ResponseEntity<JsonNode> resposta = comoCurador()
                .postForEntity("/api/v1/admin/reviews/" + review.getId() + "/note", motivo("manter"), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("a fila vem da mais antiga para a mais recente, e filtra por status e por produto")
    void filaOrdenadaEFiltrada() {
        Review primeira = criar(acmeChat, "primeira@acme.com.br", "pt-BR", true);
        Review segunda = criar(acmeChat, "segunda@acme.com.br", "pt-BR", false);
        Review terceira = criar(acmeCrm, "terceira@acme.com.br", "pt-BR", true);

        JsonNode todas = comoCurador().getForObject("/api/v1/admin/reviews", JsonNode.class);
        assertThat(todas.get("total").asLong()).isEqualTo(3);
        assertThat(ids(todas)).containsExactly(primeira.getId(), segunda.getId(), terceira.getId());

        JsonNode pendentes =
                comoCurador().getForObject("/api/v1/admin/reviews?status=PENDING_MODERATION", JsonNode.class);
        assertThat(ids(pendentes)).containsExactly(primeira.getId(), terceira.getId());

        JsonNode doCrm = comoCurador().getForObject("/api/v1/admin/reviews?product=acme-crm", JsonNode.class);
        assertThat(ids(doCrm)).containsExactly(terceira.getId());

        JsonNode combinado = comoCurador()
                .getForObject("/api/v1/admin/reviews?status=PENDING_EMAIL&product=acme-chat", JsonNode.class);
        assertThat(ids(combinado)).containsExactly(segunda.getId());
    }

    @Test
    @DisplayName("status ou produto desconhecido no filtro responde 400, e nao lista vazia")
    void filtroDesconhecido() {
        assertThat(comoCurador()
                        .getForEntity("/api/v1/admin/reviews?status=INVENTADO", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(comoCurador()
                        .getForEntity("/api/v1/admin/reviews?product=nao-existe", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a fila traz o dominio e o sinal de repeticao, e nunca o e-mail do autor")
    void filaTrazDominioEnaoEmail() {
        criar(acmeChat, "ana@acme.com.br", "pt-BR", true);
        criar(acmeChat, "bia@acme.com.br", "pt-BR", true);
        criar(acmeChat, "carlos@outra.com.br", "pt-BR", true);

        JsonNode fila = comoCurador().getForObject("/api/v1/admin/reviews?product=acme-chat", JsonNode.class);

        JsonNode primeira = fila.get("items").get(0);
        assertThat(primeira.get("author_domain").asText()).isEqualTo("acme.com.br");
        assertThat(primeira.get("outras_do_mesmo_dominio").asLong()).isEqualTo(1);
        assertThat(fila.get("items").get(2).get("outras_do_mesmo_dominio").asLong())
                .isZero();

        // O e-mail nao aparece em lugar nenhum do corpo, nem dentro dos itens.
        assertThat(fila.toString()).doesNotContain("ana@acme.com.br").doesNotContain("author_email");
    }

    private static List<UUID> ids(JsonNode envelope) {
        return envelope.get("items").findValuesAsText("id").stream()
                .map(UUID::fromString)
                .toList();
    }
}
