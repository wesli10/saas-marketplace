package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
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

/** Criterio de verificacao da tarefa 001-T21: AC-21 e a curadoria de categorias. */
class AdminCategoriaIT extends AbstractIntegrationTest {

    private static final String USUARIO = "curador-teste";
    private static final String SENHA = "senha-de-teste";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private JdbcTemplate jdbc;

    private Category raiz;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        raiz = categorias.save(new Category("ia-e-automacao", "ai-and-automation", "IA", "AI", null));
    }

    private TestRestTemplate curador() {
        return http.withBasicAuth(USUARIO, SENHA);
    }

    private Map<String, Object> corpoDe(String slugPt, String slugEn, UUID pai) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug_pt", slugPt);
        corpo.put("slug_en", slugEn);
        corpo.put("name_pt", "Nome em portugues");
        corpo.put("name_en", "Name in english");
        corpo.put("parent_id", pai);
        return corpo;
    }

    private ResponseEntity<JsonNode> criar(Map<String, Object> corpo) {
        return curador().postForEntity("/api/v1/admin/categories", corpo, JsonNode.class);
    }

    private Category subcategoria(String slugPt, String slugEn) {
        return categorias.saveAndFlush(new Category(slugPt, slugEn, "Sub", "Sub", raiz));
    }

    private Product produtoEm(Category categoria) {
        Product produto = new Product(
                "acme-chat",
                "Acme Chat",
                categoria,
                "tagline pt",
                "tagline en",
                "descricao pt",
                "description en",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing");
        produto.setStatus(ProductStatus.DRAFT);
        return produtos.saveAndFlush(produto);
    }

    @Test
    @DisplayName("AC-17: as rotas de categoria exigem credencial de curadoria")
    void todasAsRotasExigemBasic() {
        Category sub = subcategoria("atendimento", "customer-support");

        assertThat(http.getForEntity("/api/v1/admin/categories", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.postForEntity("/api/v1/admin/categories", corpoDe("novo", "new", null), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.exchange(
                                "/api/v1/admin/categories/" + sub.getId(),
                                HttpMethod.PUT,
                                new HttpEntity<>(corpoDe("atendimento", "customer-support", null)),
                                String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        assertThat(http.exchange("/api/v1/admin/categories/" + sub.getId(), HttpMethod.DELETE, null, String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("cria uma subcategoria e ela aparece na listagem com os dois idiomas")
    void criaEDepoisLista() {
        ResponseEntity<JsonNode> criada = criar(corpoDe("atendimento", "customer-support", raiz.getId()));

        assertThat(criada.getStatusCode().value()).isEqualTo(201);
        assertThat(criada.getBody().get("slug_pt").asText()).isEqualTo("atendimento");
        assertThat(criada.getBody().get("slug_en").asText()).isEqualTo("customer-support");
        assertThat(criada.getBody().get("parent_id").asText())
                .isEqualTo(raiz.getId().toString());

        JsonNode lista = curador()
                .getForEntity("/api/v1/admin/categories", JsonNode.class)
                .getBody();
        List<String> slugs = new ArrayList<>();
        lista.forEach(item -> slugs.add(item.get("slug_pt").asText()));
        assertThat(slugs).containsExactlyInAnyOrder("ia-e-automacao", "atendimento");

        // A curadoria enxerga os dois idiomas de uma vez, ao contrario da rota publica.
        assertThat(lista.get(0).has("name_pt")).isTrue();
        assertThat(lista.get(0).has("name_en")).isTrue();
    }

    @Test
    @DisplayName("corpo sem os campos obrigatorios responde 400 com errors[] em snake_case")
    void corpoInvalido() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("slug_pt", "atendimento");
        corpo.put("slug_en", "customer-support");
        corpo.put("name_pt", "");

        ResponseEntity<JsonNode> resposta = criar(corpo);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        List<String> campos = new ArrayList<>();
        resposta.getBody()
                .get("errors")
                .forEach(erro -> campos.add(erro.get("field").asText()));
        assertThat(campos).contains("name_pt", "name_en");
    }

    @Test
    @DisplayName("criar sem slug responde 400 apontando slug_pt e slug_en")
    void criarSemSlug() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name_pt", "Nome");
        corpo.put("name_en", "Name");

        ResponseEntity<JsonNode> resposta = criar(corpo);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        List<String> campos = new ArrayList<>();
        resposta.getBody()
                .get("errors")
                .forEach(erro -> campos.add(erro.get("field").asText()));
        assertThat(campos).containsExactlyInAnyOrder("slug_pt", "slug_en");
    }

    @Test
    @DisplayName("slug ja usado por outra categoria, mesmo no outro idioma, responde 409")
    void slugColidindoEntreIdiomas() {
        subcategoria("atendimento", "customer-support");

        // O slug_pt da nova bate com o slug_en da existente: a rota publica ficaria
        // ambigua, e a tabela de lookup (D-01) e quem garante isso.
        ResponseEntity<JsonNode> resposta = criar(corpoDe("customer-support", "outro-slug", raiz.getId()));

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/conflict");
        assertThat(resposta.getBody().get("detail").asText()).contains("customer-support");
    }

    @Test
    @DisplayName("criar com pai que ja e subcategoria responde 409: profundidade maxima e 2")
    void paiQueJaEhSubcategoria() {
        Category sub = subcategoria("atendimento", "customer-support");

        ResponseEntity<JsonNode> resposta = criar(corpoDe("chatbots", "chatbots", sub.getId()));

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("detail").asText())
                .contains(sub.getId().toString());
    }

    @Test
    @DisplayName("criar com pai inexistente responde 404")
    void paiInexistente() {
        assertThat(criar(corpoDe("chatbots", "chatbots", UUID.randomUUID()))
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("PUT muda nome e descricao, e o slug continua o mesmo")
    void atualizaNomes() {
        Category sub = subcategoria("atendimento", "customer-support");

        Map<String, Object> corpo = corpoDe("atendimento", "customer-support", raiz.getId());
        corpo.put("name_pt", "Atendimento e chatbots");
        corpo.put("description_pt", "Ferramentas de atendimento");

        ResponseEntity<JsonNode> resposta = curador()
                .exchange(
                        "/api/v1/admin/categories/" + sub.getId(),
                        HttpMethod.PUT,
                        new HttpEntity<>(corpo),
                        JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody().get("name_pt").asText()).isEqualTo("Atendimento e chatbots");
        assertThat(resposta.getBody().get("description_pt").asText()).isEqualTo("Ferramentas de atendimento");
        assertThat(resposta.getBody().get("slug_pt").asText()).isEqualTo("atendimento");
    }

    @Test
    @DisplayName("PUT tentando trocar o slug responde 409, em vez de ignorar o campo")
    void slugNaoMuda() {
        Category sub = subcategoria("atendimento", "customer-support");

        ResponseEntity<JsonNode> resposta = curador()
                .exchange(
                        "/api/v1/admin/categories/" + sub.getId(),
                        HttpMethod.PUT,
                        new HttpEntity<>(corpoDe("outro-slug", "customer-support", raiz.getId())),
                        JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("detail").asText()).contains("atendimento");
    }

    @Test
    @DisplayName("AC-21: excluir categoria com produto vinculado responde 409")
    void exclusaoComProduto() {
        Category sub = subcategoria("atendimento", "customer-support");
        produtoEm(sub);

        ResponseEntity<JsonNode> resposta =
                curador().exchange("/api/v1/admin/categories/" + sub.getId(), HttpMethod.DELETE, null, JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().get("type").asText()).endsWith("/problems/conflict");
        assertThat(categorias.existsById(sub.getId())).isTrue();
    }

    @Test
    @DisplayName("AC-21: excluir categoria com subcategoria responde 409")
    void exclusaoComFilha() {
        subcategoria("atendimento", "customer-support");

        ResponseEntity<JsonNode> resposta =
                curador().exchange("/api/v1/admin/categories/" + raiz.getId(), HttpMethod.DELETE, null, JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(categorias.existsById(raiz.getId())).isTrue();
    }

    @Test
    @DisplayName("AC-21: excluir categoria vazia responde 204 e ela some de verdade")
    void exclusaoDeCategoriaVazia() {
        Category sub = subcategoria("atendimento", "customer-support");

        ResponseEntity<Void> resposta =
                curador().exchange("/api/v1/admin/categories/" + sub.getId(), HttpMethod.DELETE, null, Void.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(204);
        assertThat(categorias.existsById(sub.getId())).isFalse();
        // O lookup de slug cai junto, pelo ON DELETE CASCADE.
        assertThat(categorias.buscarIdPorSlugEmQualquerIdioma("atendimento")).isEmpty();
    }

    @Test
    @DisplayName("excluir categoria inexistente responde 404")
    void exclusaoDeInexistente() {
        assertThat(curador()
                        .exchange(
                                "/api/v1/admin/categories/" + UUID.randomUUID(), HttpMethod.DELETE, null, String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }
}
