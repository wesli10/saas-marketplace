package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.Tag;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T22: curadoria de integracoes e tags. */
class AdminVocabularioIT extends AbstractIntegrationTest {

    private static final String USUARIO = "curador-teste";
    private static final String SENHA = "senha-de-teste";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private TagRepository tags;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
    }

    private TestRestTemplate curador() {
        return http.withBasicAuth(USUARIO, SENHA);
    }

    private Map<String, Object> corpoDeIntegracao(String slug, String nome) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug", slug);
        corpo.put("name", nome);
        return corpo;
    }

    private Map<String, Object> corpoDeTag(String slug, String nomePt, String nomeEn) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug", slug);
        corpo.put("name_pt", nomePt);
        corpo.put("name_en", nomeEn);
        return corpo;
    }

    private List<String> campos(JsonNode corpo) {
        List<String> encontrados = new ArrayList<>();
        corpo.get("errors").forEach(erro -> encontrados.add(erro.get("field").asText()));
        return encontrados;
    }

    @Test
    @DisplayName("AC-17: criar e listar vocabulario exigem credencial de curadoria")
    void semCredencialNaoEntra() {
        assertThat(http.getForEntity("/api/v1/admin/integrations", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.postForEntity(
                                "/api/v1/admin/integrations", corpoDeIntegracao("whatsapp", "WhatsApp"), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.getForEntity("/api/v1/admin/tags", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.postForEntity("/api/v1/admin/tags", corpoDeTag("chatbot", "Chatbot", "Chatbot"), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("cria integracao e ela aparece na listagem, ordenada por nome")
    void criaEListaIntegracoes() {
        ResponseEntity<JsonNode> criada = curador()
                .postForEntity("/api/v1/admin/integrations", corpoDeIntegracao("whatsapp", "WhatsApp"), JsonNode.class);
        curador().postForEntity("/api/v1/admin/integrations", corpoDeIntegracao("hubspot", "HubSpot"), JsonNode.class);

        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        assertThat(criada.getBody().get("slug").asText()).isEqualTo("whatsapp");
        assertThat(criada.getBody().get("id").isNull()).isFalse();

        JsonNode lista = curador()
                .getForEntity("/api/v1/admin/integrations", JsonNode.class)
                .getBody();
        List<String> nomes = new ArrayList<>();
        lista.forEach(item -> nomes.add(item.get("name").asText()));
        assertThat(nomes).containsExactly("HubSpot", "WhatsApp");
        assertThat(integracoes.findBySlug("whatsapp")).isPresent();
    }

    @Test
    @DisplayName("integracao com slug repetido responde 409")
    void slugDeIntegracaoRepetido() {
        integracoes.save(new Integration("whatsapp", "WhatsApp"));

        ResponseEntity<JsonNode> resposta = curador()
                .postForEntity(
                        "/api/v1/admin/integrations",
                        corpoDeIntegracao("whatsapp", "WhatsApp Business"),
                        JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/conflict");
        assertThat(resposta.getBody().get("detail").asText()).contains("whatsapp");
        assertThat(integracoes.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("integracao com corpo invalido responde 400 com errors[] apontando o campo")
    void corpoDeIntegracaoInvalido() {
        ResponseEntity<JsonNode> semNome = curador()
                .postForEntity("/api/v1/admin/integrations", corpoDeIntegracao("whatsapp", ""), JsonNode.class);
        assertThat(semNome.getStatusCode().value()).isEqualTo(400);
        assertThat(campos(semNome.getBody())).contains("name");

        // Slug fora do kebab-case: o banco tambem barraria, mas com 500 em vez de 400.
        ResponseEntity<JsonNode> slugTorto = curador()
                .postForEntity(
                        "/api/v1/admin/integrations", corpoDeIntegracao("What's App", "WhatsApp"), JsonNode.class);
        assertThat(slugTorto.getStatusCode().value()).isEqualTo(400);
        assertThat(campos(slugTorto.getBody())).contains("slug");
    }

    @Test
    @DisplayName("cria tag com os dois idiomas e lista ordenada pelo nome em portugues")
    void criaEListaTags() {
        ResponseEntity<JsonNode> criada = curador()
                .postForEntity(
                        "/api/v1/admin/tags",
                        corpoDeTag("ia-generativa", "IA generativa", "Generative AI"),
                        JsonNode.class);
        curador().postForEntity("/api/v1/admin/tags", corpoDeTag("chatbot", "Chatbot", "Chatbot"), JsonNode.class);

        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        assertThat(criada.getBody().get("name_pt").asText()).isEqualTo("IA generativa");
        assertThat(criada.getBody().get("name_en").asText()).isEqualTo("Generative AI");

        JsonNode lista =
                curador().getForEntity("/api/v1/admin/tags", JsonNode.class).getBody();
        List<String> nomes = new ArrayList<>();
        lista.forEach(item -> nomes.add(item.get("name_pt").asText()));
        assertThat(nomes).containsExactly("Chatbot", "IA generativa");
    }

    @Test
    @DisplayName("tag com slug repetido responde 409")
    void slugDeTagRepetido() {
        tags.save(new Tag("chatbot", "Chatbot", "Chatbot"));

        ResponseEntity<JsonNode> resposta =
                curador().postForEntity("/api/v1/admin/tags", corpoDeTag("chatbot", "Bot", "Bot"), JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(tags.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("tag sem o nome em ingles responde 400 nomeando name_en")
    void tagSemUmDosIdiomas() {
        ResponseEntity<JsonNode> resposta =
                curador().postForEntity("/api/v1/admin/tags", corpoDeTag("chatbot", "Chatbot", null), JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(campos(resposta.getBody())).contains("name_en");
    }
}
