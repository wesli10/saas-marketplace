package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.Tag;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
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

/** Criterio de verificacao da tarefa 001-T23: AC-18, AC-20 e AC-22 pela API. */
class AdminProdutoIT extends AbstractIntegrationTest {

    private static final String USUARIO = "curador-teste";
    private static final String SENHA = "senha-de-teste";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private TagRepository tags;

    @Autowired
    private JdbcTemplate jdbc;

    private Category atendimento;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        integracoes.save(new Integration("whatsapp", "WhatsApp"));
        tags.save(new Tag("chatbot", "Chatbot", "Chatbot"));
    }

    private TestRestTemplate curador() {
        return http.withBasicAuth(USUARIO, SENHA);
    }

    private Map<String, Object> corpo(String slug) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug", slug);
        corpo.put("name", "Acme Chat");
        corpo.put("tagline_pt", "atendimento ao cliente no whatsapp");
        corpo.put("tagline_en", "customer support on whatsapp");
        corpo.put("description_pt", "Plataforma de atendimento com chatbot.");
        corpo.put("description_en", "Customer support platform with chatbot.");
        corpo.put("category_id", atendimento.getId());
        corpo.put("website_url", "https://acme.com");
        corpo.put("pricing_model", "SUBSCRIPTION");
        corpo.put("source_note", "https://acme.com/pricing - coletado em 2026-08-20");
        corpo.put("integration_slugs", List.of("whatsapp"));
        corpo.put("tag_slugs", List.of("chatbot"));
        return corpo;
    }

    private ResponseEntity<JsonNode> criar(Map<String, Object> corpo) {
        return curador().postForEntity("/api/v1/admin/products", corpo, JsonNode.class);
    }

    private ResponseEntity<JsonNode> atualizar(UUID id, Map<String, Object> corpo) {
        return curador()
                .exchange("/api/v1/admin/products/" + id, HttpMethod.PUT, new HttpEntity<>(corpo), JsonNode.class);
    }

    private UUID criarPublicado(String slug) {
        UUID id = UUID.fromString(criar(corpo(slug)).getBody().get("id").asText());
        // Publicar e assunto do T24; aqui basta o estado, direto no banco.
        jdbc.update("update product set status = 'PUBLISHED', published_at = now() where id = ?", id);
        return id;
    }

    private List<String> campos(JsonNode corpo) {
        List<String> encontrados = new ArrayList<>();
        corpo.get("errors").forEach(erro -> encontrados.add(erro.get("field").asText()));
        return encontrados;
    }

    private List<String> slugsDaBusca(String consulta) {
        JsonNode corpo =
                http.getForEntity("/api/v1/products" + consulta, JsonNode.class).getBody();
        List<String> encontrados = new ArrayList<>();
        corpo.get("items").forEach(item -> encontrados.add(item.get("slug").asText()));
        return encontrados;
    }

    @Test
    @DisplayName("AC-17: as rotas de produto exigem credencial de curadoria")
    void semCredencialNaoEntra() {
        assertThat(http.getForEntity("/api/v1/admin/products", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.postForEntity("/api/v1/admin/products", corpo("acme-chat"), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("POST cria sempre em DRAFT, com os vinculos e sem published_at")
    void criaEmRascunho() {
        ResponseEntity<JsonNode> resposta = criar(corpo("acme-chat"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        JsonNode produto = resposta.getBody();
        assertThat(produto.get("status").asText()).isEqualTo("DRAFT");
        assertThat(produto.get("published_at").isNull()).isTrue();
        assertThat(produto.get("source_note").asText()).contains("acme.com/pricing");

        List<String> integracoesDoProduto = new ArrayList<>();
        produto.get("integration_slugs").forEach(item -> integracoesDoProduto.add(item.asText()));
        assertThat(integracoesDoProduto).containsExactly("whatsapp");

        // Rascunho nao aparece na rota publica.
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("AC-18: POST sem tagline_en responde 400 com errors[] apontando o campo")
    void corpoSemTaglineEn() {
        Map<String, Object> incompleto = corpo("acme-chat");
        incompleto.remove("tagline_en");

        ResponseEntity<JsonNode> resposta = criar(incompleto);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/validation-failed");
        assertThat(campos(resposta.getBody())).containsExactly("tagline_en");
    }

    @Test
    @DisplayName("source_note e obrigatorio ja no rascunho: dado sem origem nao entra")
    void sourceNoteObrigatorio() {
        Map<String, Object> semOrigem = corpo("acme-chat");
        semOrigem.remove("source_note");

        ResponseEntity<JsonNode> resposta = criar(semOrigem);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(campos(resposta.getBody())).contains("source_note");
    }

    @Test
    @DisplayName("vinculo com slug inexistente responde 400 dizendo qual")
    void vinculoDesconhecido() {
        Map<String, Object> comIntegracaoTorta = corpo("acme-chat");
        comIntegracaoTorta.put("integration_slugs", List.of("whatsapp", "nao-existe"));

        ResponseEntity<JsonNode> resposta = criar(comIntegracaoTorta);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().get("detail").asText()).contains("nao-existe");
        assertThat(produtos.count()).isZero();
    }

    @Test
    @DisplayName("slug repetido responde 409")
    void slugRepetido() {
        criar(corpo("acme-chat"));

        assertThat(criar(corpo("acme-chat")).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("AC-20: mudar o slug de um produto PUBLISHED responde 409")
    void slugDePublicadoNaoMuda() {
        UUID id = criarPublicado("acme-chat");

        ResponseEntity<JsonNode> resposta = atualizar(id, corpo("outro-slug"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("detail").asText()).contains("acme-chat");
        assertThat(produtos.existsBySlug("acme-chat")).isTrue();
    }

    @Test
    @DisplayName("AC-20: mudar o slug de um DRAFT funciona: ainda da para corrigir")
    void slugDeRascunhoMuda() {
        UUID id = UUID.fromString(criar(corpo("acme-chat")).getBody().get("id").asText());

        ResponseEntity<JsonNode> resposta = atualizar(id, corpo("acme-chat-pro"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody().get("slug").asText()).isEqualTo("acme-chat-pro");
        assertThat(produtos.existsBySlug("acme-chat")).isFalse();
    }

    @Test
    @DisplayName("PUT substitui os vinculos, em vez de acumular")
    void vinculosSaoSubstituidos() {
        integracoes.save(new Integration("hubspot", "HubSpot"));
        UUID id = UUID.fromString(criar(corpo("acme-chat")).getBody().get("id").asText());

        Map<String, Object> trocado = corpo("acme-chat");
        trocado.put("integration_slugs", List.of("hubspot"));
        trocado.put("tag_slugs", List.of());

        JsonNode produto = atualizar(id, trocado).getBody();

        List<String> integracoesDoProduto = new ArrayList<>();
        produto.get("integration_slugs").forEach(item -> integracoesDoProduto.add(item.asText()));
        assertThat(integracoesDoProduto).containsExactly("hubspot");
        assertThat(produto.get("tag_slugs")).isEmpty();
    }

    @Test
    @DisplayName("GET ?status=DRAFT filtra; sem status, traz todos")
    void listagemPorStatus() {
        criar(corpo("acme-chat"));
        criarPublicado("zeta-desk");

        JsonNode todos =
                curador().getForEntity("/api/v1/admin/products", JsonNode.class).getBody();
        assertThat(todos.get("total").asInt()).isEqualTo(2);
        assertThat(todos.get("page").asInt()).isZero();
        assertThat(todos.get("size").asInt()).isEqualTo(20);

        JsonNode rascunhos = curador()
                .getForEntity("/api/v1/admin/products?status=DRAFT", JsonNode.class)
                .getBody();
        assertThat(rascunhos.get("total").asInt()).isEqualTo(1);
        assertThat(rascunhos.get("items").get(0).get("slug").asText()).isEqualTo("acme-chat");

        JsonNode publicados = curador()
                .getForEntity("/api/v1/admin/products?status=PUBLISHED", JsonNode.class)
                .getBody();
        assertThat(publicados.get("items").get(0).get("slug").asText()).isEqualTo("zeta-desk");
    }

    @Test
    @DisplayName("status desconhecido na listagem responde 400")
    void statusInvalido() {
        ResponseEntity<String> resposta =
                curador().getForEntity("/api/v1/admin/products?status=RASCUNHO", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("/problems/invalid-parameter").contains("status");
    }

    @Test
    @DisplayName("AC-22: editar a descricao de um publicado muda a busca na requisicao seguinte")
    void edicaoRefleteNaBusca() {
        UUID id = criarPublicado("acme-chat");

        assertThat(slugsDaBusca("?q=telemedicina")).isEmpty();

        Map<String, Object> editado = corpo("acme-chat");
        editado.put("description_pt", "Plataforma de telemedicina para clinicas.");
        assertThat(atualizar(id, editado).getStatusCode().value()).isEqualTo(200);

        // Sem passo intermediario nenhum: o tsvector e recalculado por trigger (DT-06).
        assertThat(slugsDaBusca("?q=telemedicina")).containsExactly("acme-chat");
    }

    @Test
    @DisplayName("produto inexistente responde 404 no GET e no PUT")
    void inexistente() {
        UUID id = UUID.randomUUID();

        assertThat(curador()
                        .getForEntity("/api/v1/admin/products/" + id, String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
        assertThat(atualizar(id, corpo("acme-chat")).getStatusCode().value()).isEqualTo(404);
    }
}
