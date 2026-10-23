package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T24: AC-19 e o ciclo de publicacao. */
class AdminPublicacaoIT extends AbstractIntegrationTest {

    private static final String USUARIO = "curador-teste";
    private static final String SENHA = "senha-de-teste";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductContentRepository conteudos;

    @Autowired
    private JdbcTemplate jdbc;

    private Category atendimento;
    private UUID produtoId;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        produtoId = UUID.fromString(criar(corpo()).getBody().get("id").asText());
    }

    private TestRestTemplate curador() {
        return http.withBasicAuth(USUARIO, SENHA);
    }

    private Map<String, Object> corpo() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug", "acme-chat");
        corpo.put("name", "Acme Chat");
        corpo.put("tagline_pt", "atendimento ao cliente");
        corpo.put("tagline_en", "customer support");
        corpo.put("description_pt", "Plataforma de atendimento.");
        corpo.put("description_en", "Customer support platform.");
        corpo.put("category_id", atendimento.getId());
        corpo.put("website_url", "https://acme.com");
        corpo.put("pricing_model", "SUBSCRIPTION");
        corpo.put("price_min", 99.00);
        corpo.put("price_currency", "BRL");
        corpo.put("price_period", "MONTH");
        corpo.put("supported_languages", List.of("pt", "en"));
        corpo.put("source_note", "https://acme.com/pricing - coletado em 2026-08-20");
        return corpo;
    }

    private ResponseEntity<JsonNode> criar(Map<String, Object> corpo) {
        return curador().postForEntity("/api/v1/admin/products", corpo, JsonNode.class);
    }

    private ResponseEntity<JsonNode> curar(String locale) {
        Map<String, Object> conteudo = new LinkedHashMap<>();
        conteudo.put("pros", List.of("integra com whatsapp"));
        conteudo.put("cons", List.of("sem plano gratuito"));
        conteudo.put("best_for", "Equipes pequenas");
        conteudo.put("faq", List.of(Map.of("question", "Tem teste?", "answer", "Sim.")));
        return curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId + "/content/" + locale,
                        HttpMethod.PUT,
                        new HttpEntity<>(conteudo),
                        JsonNode.class);
    }

    private ResponseEntity<JsonNode> transicao(String acao) {
        return curador().postForEntity("/api/v1/admin/products/" + produtoId + "/" + acao, null, JsonNode.class);
    }

    private List<String> campos(JsonNode corpo) {
        List<String> encontrados = new ArrayList<>();
        corpo.get("errors").forEach(erro -> encontrados.add(erro.get("field").asText()));
        return encontrados;
    }

    private void curarOsDoisIdiomas() {
        assertThat(curar("pt-BR").getStatusCode().value()).isEqualTo(204);
        assertThat(curar("en").getStatusCode().value()).isEqualTo(204);
    }

    @Test
    @DisplayName("AC-19: publicar sem conteudo em ingles responde 422 listando a pendencia")
    void publicarSemConteudoEmIngles() {
        curar("pt-BR");

        ResponseEntity<JsonNode> resposta = transicao("publish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/not-publishable");
        assertThat(campos(resposta.getBody())).containsExactly("product_content.en");
    }

    @Test
    @DisplayName("AC-19: preenchidos os dois idiomas, publica e published_at fica setado")
    void publicaDepoisDeCurar() {
        curarOsDoisIdiomas();

        ResponseEntity<JsonNode> resposta = transicao("publish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody().get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(resposta.getBody().get("published_at").isNull()).isFalse();

        // E agora aparece na rota publica.
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("publicar de novo nao altera o published_at da primeira vez")
    void publicarDeNovoNaoMexeNaData() {
        curarOsDoisIdiomas();
        String primeira = transicao("publish").getBody().get("published_at").asText();

        ResponseEntity<JsonNode> denovo = transicao("publish");

        assertThat(denovo.getStatusCode().value()).isEqualTo(200);
        assertThat(denovo.getBody().get("published_at").asText()).isEqualTo(primeira);
    }

    @Test
    @DisplayName("AC-19: SUBSCRIPTION sem price_min responde 422")
    void assinaturaSemPreco() {
        curarOsDoisIdiomas();

        Map<String, Object> semPreco = corpo();
        semPreco.remove("price_min");
        semPreco.remove("price_currency");
        semPreco.remove("price_period");
        curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId,
                        HttpMethod.PUT,
                        new HttpEntity<>(semPreco),
                        JsonNode.class);

        ResponseEntity<JsonNode> resposta = transicao("publish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(campos(resposta.getBody())).containsExactly("price_min");
    }

    @Test
    @DisplayName("as pendencias saem todas de uma vez, nao uma por requisicao")
    void todasAsPendenciasJuntas() {
        Map<String, Object> incompleto = corpo();
        incompleto.remove("price_min");
        incompleto.remove("price_currency");
        incompleto.remove("price_period");
        incompleto.put("supported_languages", List.of());
        curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId,
                        HttpMethod.PUT,
                        new HttpEntity<>(incompleto),
                        JsonNode.class);

        ResponseEntity<JsonNode> resposta = transicao("publish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(422);
        assertThat(campos(resposta.getBody()))
                .containsExactlyInAnyOrder(
                        "product_content.pt-BR", "product_content.en", "supported_languages", "price_min");
    }

    @Test
    @DisplayName("unpublish volta para DRAFT e o produto some da rota publica")
    void despublicar() {
        curarOsDoisIdiomas();
        transicao("publish");

        ResponseEntity<JsonNode> resposta = transicao("unpublish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody().get("status").asText()).isEqualTo("DRAFT");
        // A data da primeira publicacao continua la: o produto ja esteve no ar.
        assertThat(resposta.getBody().get("published_at").isNull()).isFalse();
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("archive tambem tira o produto da rota publica")
    void arquivar() {
        curarOsDoisIdiomas();
        transicao("publish");

        ResponseEntity<JsonNode> resposta = transicao("archive");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody().get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("transicao invalida responde 409: arquivado nao volta ao ar por publish")
    void transicaoInvalida() {
        curarOsDoisIdiomas();
        transicao("archive");

        ResponseEntity<JsonNode> resposta = transicao("publish");

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("detail").asText()).contains("ARCHIVED");
    }

    @Test
    @DisplayName("despublicar um rascunho responde 409: nao ha o que tirar do ar")
    void despublicarRascunho() {
        assertThat(transicao("unpublish").getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("o conteudo e substituido, e nao mesclado")
    void conteudoESubstituido() {
        curar("pt-BR");

        Map<String, Object> menor = new LinkedHashMap<>();
        menor.put("pros", List.of("so este"));
        curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId + "/content/pt-BR",
                        HttpMethod.PUT,
                        new HttpEntity<>(menor),
                        JsonNode.class);

        curarOsDoisIdiomas();
        curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId + "/content/pt-BR",
                        HttpMethod.PUT,
                        new HttpEntity<>(menor),
                        JsonNode.class);
        transicao("publish");

        JsonNode ficha =
                http.getForEntity("/api/v1/products/acme-chat", JsonNode.class).getBody();
        assertThat(ficha.get("pros")).hasSize(1);
        // O que sumiu do corpo sumiu do produto.
        assertThat(ficha.get("cons")).isEmpty();
        assertThat(ficha.get("best_for").isNull()).isTrue();
    }

    @Test
    @DisplayName("locale fora de pt-BR e en na rota responde 400")
    void localeInvalidoNaRota() {
        ResponseEntity<JsonNode> resposta = curar("fr");

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/unsupported-locale");
    }

    @Test
    @DisplayName("corpo de conteudo acima dos limites responde 400 com o campo")
    void conteudoAcimaDoLimite() {
        Map<String, Object> demais = new LinkedHashMap<>();
        demais.put("pros", List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"));

        ResponseEntity<JsonNode> resposta = curador()
                .exchange(
                        "/api/v1/admin/products/" + produtoId + "/content/pt-BR",
                        HttpMethod.PUT,
                        new HttpEntity<>(demais),
                        JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(campos(resposta.getBody())).contains("pros");
    }

    @Test
    @DisplayName("AC-17: as rotas do ciclo de publicacao exigem credencial")
    void semCredencial() {
        assertThat(http.postForEntity("/api/v1/admin/products/" + produtoId + "/publish", null, String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.exchange(
                                "/api/v1/admin/products/" + produtoId + "/content/pt-BR",
                                HttpMethod.PUT,
                                new HttpEntity<>(Map.of("pros", List.of())),
                                String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("conteudo em produto inexistente responde 404")
    void conteudoDeProdutoInexistente() {
        UUID inexistente = UUID.randomUUID();

        ResponseEntity<String> resposta = curador()
                .exchange(
                        "/api/v1/admin/products/" + inexistente + "/content/pt-BR",
                        HttpMethod.PUT,
                        new HttpEntity<>(Map.of("pros", List.of("a"))),
                        String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(conteudos.count()).isZero();
    }
}
