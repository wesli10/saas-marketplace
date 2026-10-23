package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import br.com.saasfinder.reviews.domain.TokenDeConfirmacao;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
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

/**
 * Criterio de verificacao da tarefa 003-T04: AC-01, AC-02, AC-05, AC-06 e AC-07.
 *
 * <p>O enviador e dublado - e o "enviador falso" que a tarefa pede. Nao e para
 * evitar I/O: e para poder <b>ler o e-mail que sairia</b> (o token em claro so
 * existe ali) e para faze-lo falhar de proposito no teste do AC-07.
 */
class ReviewEnvioIT extends AbstractIntegrationTest {

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

    private String json(String email, String corpo, int nota) {
        return """
                {
                  "rating": %d,
                  "would_recommend": true,
                  "title": "Resolveu o atendimento",
                  "body": "%s",
                  "author_name": "Ana Ribeiro",
                  "author_email": "%s"
                }
                """
                .formatted(nota, corpo, email);
    }

    private ResponseEntity<String> enviar(String corpoJson, String idioma) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (idioma != null) {
            cabecalhos.setAcceptLanguage(List.of(new java.util.Locale.LanguageRange(idioma)));
        }
        return http.postForEntity(
                "/api/v1/products/acme-chat/reviews", new HttpEntity<>(corpoJson, cabecalhos), String.class);
    }

    @Test
    @DisplayName("envio valido responde 202 e cria a review em PENDING_EMAIL (AC-01, AC-02)")
    void envioValido() {
        ResponseEntity<String> resposta = enviar(json("ana@acme.com.br", CORPO_VALIDO, 4), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        List<Review> gravadas = reviews.findAll();
        assertThat(gravadas).hasSize(1);
        Review review = gravadas.get(0);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        assertThat(review.getAuthorEmail()).isEqualTo("ana@acme.com.br");
        assertThat(review.getAuthorDomain()).isEqualTo("acme.com.br");
        assertThat(review.isVerified()).isFalse();
        assertThat(review.getConfirmedAt()).isNull();
        assertThat(review.getLocale()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("o e-mail sai depois do commit, com o link, e no idioma da pagina (AC-01, AC-16)")
    void emailSaiComOLink() {
        enviar(json("john@acme.com", CORPO_VALIDO, 5), "en");

        ArgumentCaptor<Email> enviado = ArgumentCaptor.forClass(Email.class);
        verify(enviador).enviar(enviado.capture());

        Email email = enviado.getValue();
        assertThat(email.destinatario()).isEqualTo("john@acme.com");
        assertThat(email.assunto()).isEqualTo("Confirm your review of Acme Chat");
        assertThat(email.corpo()).contains("/en/reviews/confirm?token=");
    }

    @Test
    @DisplayName("o banco guarda o hash, e nunca o token que foi para o e-mail")
    void bancoGuardaSoOHash() {
        enviar(json("ana@acme.com.br", CORPO_VALIDO, 4), null);

        ArgumentCaptor<Email> enviado = ArgumentCaptor.forClass(Email.class);
        verify(enviador).enviar(enviado.capture());

        String corpo = enviado.getValue().corpo();
        String token =
                // O link carrega token e produto: parar no `&` evita levar o slug junto.
                corpo.substring(corpo.indexOf("?token=") + "?token=".length()).split("[&\\s]")[0];
        String guardado = jdbc.queryForObject("select token_hash from review_verification", String.class);

        assertThat(token).isNotBlank();
        assertThat(guardado).isNotEqualTo(token).isEqualTo(TokenDeConfirmacao.hashDe(token));
        // O que esta no banco nao serve para montar o link de volta.
        assertThat(guardado).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("segundo envio do mesmo e-mail responde 202 igual e nao cria a segunda review (AC-05)")
    void segundoEnvioEIdentico() {
        ResponseEntity<String> primeira = enviar(json("ana@acme.com.br", CORPO_VALIDO, 4), null);
        ResponseEntity<String> segunda = enviar(
                json(
                        "Ana@ACME.com.BR",
                        "Outro texto completamente diferente do primeiro, com folga suficiente para o piso de oitenta.",
                        1),
                null);

        assertThat(segunda.getStatusCode()).isEqualTo(primeira.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(segunda.getBody()).isEqualTo(primeira.getBody());

        List<Review> gravadas = reviews.findAll();
        assertThat(gravadas).hasSize(1);
        // A primeira review continua intacta: o segundo envio nao sobrescreve nada.
        assertThat(gravadas.get(0).getRating()).isEqualTo((short) 4);
        // E nenhum segundo e-mail sai - senao o segundo envio serviria de sonda.
        verify(enviador).enviar(any(Email.class));
    }

    @Test
    @DisplayName("corpo curto responde 400 em problem+json nomeando o campo (AC-06)")
    void corpoCurtoRecusado() {
        ResponseEntity<JsonNode> resposta = http.postForEntity(
                "/api/v1/products/acme-chat/reviews", comJson(json("ana@acme.com.br", "bom", 4)), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getHeaders().getContentType()).hasToString(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(campos(resposta.getBody())).contains("body");
        assertThat(reviews.count()).isZero();
        verify(enviador, never()).enviar(any(Email.class));
    }

    @Test
    @DisplayName("nota fora de 1..5 responde 400 nomeando o campo (AC-06)")
    void notaForaDaFaixaRecusada() {
        ResponseEntity<JsonNode> resposta = http.postForEntity(
                "/api/v1/products/acme-chat/reviews",
                comJson(json("ana@acme.com.br", CORPO_VALIDO, 6)),
                JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campos(resposta.getBody())).contains("rating");
        assertThat(reviews.count()).isZero();
    }

    @Test
    @DisplayName("`would_recommend` ausente responde 400: e insumo do ranking, nao enfeite")
    void recomendariaEObrigatorio() {
        String semCampo =
                """
                {
                  "rating": 4,
                  "title": "Resolveu o atendimento",
                  "body": "%s",
                  "author_name": "Ana Ribeiro",
                  "author_email": "ana@acme.com.br"
                }
                """
                        .formatted(CORPO_VALIDO);

        ResponseEntity<JsonNode> resposta =
                http.postForEntity("/api/v1/products/acme-chat/reviews", comJson(semCampo), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campos(resposta.getBody())).contains("would_recommend");
    }

    @Test
    @DisplayName("e-mail malformado responde 400 nomeando o campo")
    void emailInvalidoRecusado() {
        ResponseEntity<JsonNode> resposta = http.postForEntity(
                "/api/v1/products/acme-chat/reviews",
                comJson(json("ana-arroba-acme", CORPO_VALIDO, 4)),
                JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(campos(resposta.getBody())).contains("author_email");
    }

    @Test
    @DisplayName("falha no envio nao desfaz a review: ela fica em PENDING_EMAIL (AC-07)")
    void falhaDeEnvioNaoDesfazAReview() {
        doThrow(new IllegalStateException("SMTP fora do ar")).when(enviador).enviar(any(Email.class));

        ResponseEntity<String> resposta = enviar(json("ana@acme.com.br", CORPO_VALIDO, 4), null);

        // O envio acontece depois do commit, entao a falha nem chega ao cliente.
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        List<Review> gravadas = reviews.findAll();
        assertThat(gravadas).hasSize(1);
        assertThat(gravadas.get(0).getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        // E o token continua gravado, para o reenvio ter o que substituir.
        assertThat(jdbc.queryForObject("select count(*) from review_verification", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("slug desconhecido responde 404, e nao 202")
    void slugDesconhecido() {
        ResponseEntity<String> resposta = http.postForEntity(
                "/api/v1/products/nao-existe/reviews", comJson(json("ana@acme.com.br", CORPO_VALIDO, 4)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reviews.count()).isZero();
    }

    private HttpEntity<String> comJson(String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(corpo, cabecalhos);
    }

    private static List<String> campos(JsonNode problema) {
        return problema.path("errors").findValuesAsText("field");
    }
}
