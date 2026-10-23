package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T14: AC-05 e AC-15. */
class ProductListIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private JdbcTemplate jdbc;

    private Category categoria;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
    }

    private Product criar(String slug, ProductStatus status) {
        Product produto = new Product(
                slug,
                "Acme " + slug,
                categoria,
                "atendimento no whatsapp",
                "whatsapp support",
                "descricao em portugues",
                "description in english",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing");
        produto.setStatus(status);
        if (status == ProductStatus.PUBLISHED) {
            produto.setPublishedAt(Instant.now());
        }
        return produtos.saveAndFlush(produto);
    }

    /**
     * Slugs dos itens, so do nivel de cima. findValuesAsText desce recursivamente e
     * traria junto o slug da categoria de cada item.
     */
    private List<String> slugsDe(JsonNode corpo) {
        List<String> slugs = new ArrayList<>();
        corpo.get("items").forEach(item -> slugs.add(item.get("slug").asText()));
        return slugs;
    }

    private JsonNode listar(String consulta) {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products" + consulta, JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return resposta.getBody();
    }

    @Test
    @DisplayName("AC-05: so produtos publicados aparecem, e o total confere")
    void soPublicados() {
        criar("publicado-um", ProductStatus.PUBLISHED);
        criar("publicado-dois", ProductStatus.PUBLISHED);
        criar("rascunho", ProductStatus.DRAFT);
        criar("arquivado", ProductStatus.ARCHIVED);

        JsonNode corpo = listar("");

        assertThat(corpo.get("total").asInt()).isEqualTo(2);
        assertThat(corpo.get("items")).hasSize(2);
        assertThat(slugsDe(corpo)).containsExactlyInAnyOrder("publicado-um", "publicado-dois");
    }

    @Test
    @DisplayName("AC-05: a paginacao nao repete nem perde item")
    void paginacaoIntegra() {
        for (int i = 1; i <= 5; i++) {
            criar("produto-" + i, ProductStatus.PUBLISHED);
        }

        List<String> vistos = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            JsonNode corpo = listar("?page=" + pagina + "&size=2");
            assertThat(corpo.get("total").asInt()).isEqualTo(5);
            assertThat(corpo.get("page").asInt()).isEqualTo(pagina);
            vistos.addAll(slugsDe(corpo));
        }

        assertThat(vistos).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AC-05: pagina alem da ultima traz o total certo, e nao zero")
    void totalSobreviveAOffsetAlemDoFim() {
        // Criterio de verificacao da tarefa 002-T28. O total vinha de
        // `count(*) over ()`, uma coluna das linhas: sem linha, sem total. Quem
        // paginasse pela resposta - como a tela do 002 faz - via "nenhum
        // resultado" onde havia cinco, e perdia o caminho de volta.
        for (int i = 1; i <= 5; i++) {
            criar("produto-" + i, ProductStatus.PUBLISHED);
        }

        JsonNode alemDoFim = listar("?page=9&size=2");

        assertThat(alemDoFim.get("items")).isEmpty();
        assertThat(alemDoFim.get("total").asInt()).isEqualTo(5);
        assertThat(alemDoFim.get("page").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("AC-15: size acima de 50 responde 400 em problem+json")
    void sizeAcimaDoMaximo() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/products?size=51", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("/problems/invalid-parameter").contains("size");
    }

    @Test
    @DisplayName("AC-15: size=50 passa, e valores absurdos tambem sao recusados")
    void limitesDeSize() {
        assertThat(http.getForEntity("/api/v1/products?size=50", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
        assertThat(http.getForEntity("/api/v1/products?size=0", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(http.getForEntity("/api/v1/products?page=-1", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("sem parametro, vale page 0 e size 20")
    void padroesDePaginacao() {
        JsonNode corpo = listar("");

        assertThat(corpo.get("page").asInt()).isZero();
        assertThat(corpo.get("size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("sem busca, o mais recentemente atualizado vem primeiro")
    void ordenacaoPorAtualizacao() {
        criar("mais-antigo", ProductStatus.PUBLISHED);
        Product recente = criar("mais-novo", ProductStatus.PUBLISHED);

        // O trigger set_updated_at carimba now() a cada update, entao basta tocar na
        // linha para ela virar a mais recente.
        recente.setName("Acme renomeado");
        produtos.saveAndFlush(recente);

        assertThat(slugsDe(listar(""))).containsExactly("mais-novo", "mais-antigo");
    }

    @Test
    @DisplayName("o item de lista sai no idioma pedido, com categoria e integracoes")
    void itemResolvidoPorIdioma() {
        Product produto = criar("acme-chat", ProductStatus.PUBLISHED);
        Integration whatsapp = integracoes.save(new Integration("whatsapp", "WhatsApp"));
        jdbc.update("insert into product_integration values (?, ?)", produto.getId(), whatsapp.getId());

        JsonNode emPortugues = listar("?lang=pt-BR").get("items").get(0);
        assertThat(emPortugues.get("tagline").asText()).isEqualTo("atendimento no whatsapp");
        assertThat(emPortugues.get("category").get("slug").asText()).isEqualTo("atendimento");
        assertThat(emPortugues.get("category").get("name").asText()).isEqualTo("Atendimento");
        assertThat(emPortugues.get("integrations").get(0).get("slug").asText()).isEqualTo("whatsapp");

        JsonNode emIngles = listar("?lang=en").get("items").get(0);
        assertThat(emIngles.get("tagline").asText()).isEqualTo("whatsapp support");
        assertThat(emIngles.get("category").get("slug").asText()).isEqualTo("customer-support");
        assertThat(emIngles.get("category").get("name").asText()).isEqualTo("Customer support");
    }

    @Test
    @DisplayName("os campos saem em snake_case e sem source_note")
    void formatoDosCampos() {
        criar("acme-chat", ProductStatus.PUBLISHED);

        ResponseEntity<String> resposta = http.getForEntity("/api/v1/products", String.class);

        assertThat(resposta.getBody())
                .contains("\"has_free_trial\"")
                .contains("\"price_min\"")
                .contains("\"pricing_model\"")
                .doesNotContain("hasFreeTrial")
                .doesNotContain("source_note")
                .doesNotContain("https://acme.com/pricing");
    }

    @Test
    @DisplayName("lista vazia responde 200 com total zero")
    void listaVazia() {
        JsonNode corpo = listar("");

        assertThat(corpo.get("total").asInt()).isZero();
        assertThat(corpo.get("items")).isEmpty();
    }
}
