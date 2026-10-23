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
import br.com.saasfinder.reviews.domain.ReviewService;
import br.com.saasfinder.reviews.domain.ReviewStatus;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
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

/** Criterio de verificacao da tarefa 003-T05: AC-03 e AC-04, mais o expurgo da spec secao 4.2. */
class ReviewConfirmacaoIT extends AbstractIntegrationTest {

    private static final String CORPO_VALIDO =
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
    private ReviewService servico;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EnviadorDeEmail enviador;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        Category categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        produtos.save(publicado("acme-chat", "Acme Chat", categoria));
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

    /** Envia uma review e devolve o token que foi para o e-mail. */
    private String enviarEPegarToken(String email) {
        String corpo =
                """
                {
                  "rating": 4,
                  "would_recommend": true,
                  "title": "Resolveu o atendimento",
                  "body": "%s",
                  "author_name": "Ana Ribeiro",
                  "author_email": "%s"
                }
                """
                        .formatted(CORPO_VALIDO, email);

        ResponseEntity<String> resposta =
                http.postForEntity("/api/v1/products/acme-chat/reviews", comJson(corpo), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        return ultimoTokenEnviado();
    }

    private String ultimoTokenEnviado() {
        ArgumentCaptor<Email> enviado = ArgumentCaptor.forClass(Email.class);
        verify(enviador, org.mockito.Mockito.atLeastOnce()).enviar(enviado.capture());

        String corpo = enviado.getValue().corpo();
        return // O link carrega token e produto: parar no `&` evita levar o slug junto.
        corpo.substring(corpo.indexOf("?token=") + "?token=".length()).split("[&\\s]")[0];
    }

    private ResponseEntity<String> confirmar(String token) {
        return http.postForEntity(
                "/api/v1/reviews/confirm", comJson("{\"token\": \"%s\"}".formatted(token)), String.class);
    }

    private ResponseEntity<String> reenviar(String slug, String email) {
        return http.postForEntity(
                "/api/v1/reviews/resend",
                comJson("{\"product_slug\": \"%s\", \"email\": \"%s\"}".formatted(slug, email)),
                String.class);
    }

    private HttpEntity<String> comJson(String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(corpo, cabecalhos);
    }

    @Test
    @DisplayName("confirmar leva a PENDING_MODERATION; o mesmo token de novo responde 410 (AC-03)")
    void confirmarDuasVezes() {
        String token = enviarEPegarToken("ana@acme.com.br");

        assertThat(confirmar(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        Review review = reviews.findAll().get(0);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(review.getConfirmedAt()).isNotNull();
        assertThat(review.isVerified()).isTrue();

        assertThat(confirmar(token).getStatusCode()).isEqualTo(HttpStatus.GONE);
        // E a segunda tentativa nao mexeu em nada.
        assertThat(reviews.findAll().get(0).getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    }

    @Test
    @DisplayName("dominio de provedor gratuito confirma sem ganhar o selo")
    void gratuitoConfirmaSemSelo() {
        String token = enviarEPegarToken("ana@gmail.com");

        assertThat(confirmar(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        Review review = reviews.findAll().get(0);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
        assertThat(review.isVerified()).isFalse();
    }

    @Test
    @DisplayName("token desconhecido responde 410, e nao 404: os tres casos sao indistinguiveis")
    void tokenDesconhecido() {
        assertThat(confirmar("token-que-nunca-existiu").getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("token fora da validade responde 410, e o reenvio gera um que funciona (AC-04)")
    void tokenExpiradoEReenvio() {
        String expirado = enviarEPegarToken("ana@acme.com.br");

        // Emitido ha 49 h: passou da validade de 48 h.
        jdbc.update("update review_verification set expires_at = now() - interval '1 hour'");

        assertThat(confirmar(expirado).getStatusCode()).isEqualTo(HttpStatus.GONE);

        clearInvocations(enviador);
        assertThat(reenviar("acme-chat", "ana@acme.com.br").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String novo = ultimoTokenEnviado();
        assertThat(novo).isNotEqualTo(expirado);
        assertThat(confirmar(novo).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviews.findAll().get(0).getStatus()).isEqualTo(ReviewStatus.PENDING_MODERATION);
    }

    @Test
    @DisplayName("o token anterior para de valer depois do reenvio")
    void reenvioInvalidaOAnterior() {
        String primeiro = enviarEPegarToken("ana@acme.com.br");

        clearInvocations(enviador);
        reenviar("acme-chat", "ana@acme.com.br");
        String segundo = ultimoTokenEnviado();

        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(confirmar(primeiro).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(confirmar(segundo).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Continua havendo uma verificacao so: o reenvio substitui, nao acumula.
        assertThat(jdbc.queryForObject("select count(*) from review_verification", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reenvio para e-mail que nunca avaliou responde 202 e nao manda nada")
    void reenvioSemReviewESilencioso() {
        clearInvocations(enviador);

        assertThat(reenviar("acme-chat", "ninguem@acme.com.br").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(reenviar("nao-existe", "ana@acme.com.br").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        verify(enviador, never()).enviar(any(Email.class));
    }

    @Test
    @DisplayName("reenvio de review ja confirmada responde 202 e nao gera token novo")
    void reenvioDepoisDeConfirmadaESilencioso() {
        String token = enviarEPegarToken("ana@acme.com.br");
        confirmar(token);
        String hashDepoisDaConfirmacao =
                jdbc.queryForObject("select token_hash from review_verification", String.class);

        clearInvocations(enviador);
        assertThat(reenviar("acme-chat", "ana@acme.com.br").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        verify(enviador, never()).enviar(any(Email.class));
        assertThat(jdbc.queryForObject("select token_hash from review_verification", String.class))
                .isEqualTo(hashDepoisDaConfirmacao);
    }

    @Test
    @DisplayName("o expurgo apaga a nao confirmada de 31 dias e poupa a de 29 e a confirmada")
    void expurgoDosTrintaDias() {
        UUID velha = envelhecer(enviarEPegarToken("velha@acme.com.br"), 31, false);
        clearInvocations(enviador);
        UUID recente = envelhecer(enviarEPegarToken("recente@acme.com.br"), 29, false);
        clearInvocations(enviador);
        UUID confirmada = envelhecer(enviarEPegarToken("confirmada@acme.com.br"), 31, true);

        assertThat(servico.expurgarNaoConfirmadas()).isEqualTo(1);

        List<UUID> restantes = reviews.findAll().stream().map(Review::getId).toList();
        assertThat(restantes).doesNotContain(velha).contains(recente, confirmada);
        // A verificacao da apagada foi junto, pelo `on delete cascade` da V7.
        assertThat(jdbc.queryForObject(
                        "select count(*) from review_verification where review_id = ?", Integer.class, velha))
                .isZero();
    }

    /**
     * Envelhece a ultima review criada e, se pedido, confirma antes. `created_at` e
     * mantido pelo banco, entao so um UPDATE direto move o relogio para tras.
     */
    private UUID envelhecer(String token, int dias, boolean confirmar) {
        if (confirmar) {
            confirmar(token);
        }
        UUID id = reviews.findAll().stream()
                .filter(review -> review.getCreatedAt().isAfter(Instant.now().minusSeconds(300)))
                .map(Review::getId)
                .reduce((primeiro, ultimo) -> ultimo)
                .orElseThrow();

        jdbc.update("update review set created_at = now() - make_interval(days => ?) where id = ?", dias, id);
        return id;
    }
}
