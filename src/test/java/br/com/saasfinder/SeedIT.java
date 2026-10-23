package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.catalog.infra.seed.SeedRunner;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * Criterio de verificacao da tarefa 001-T25: AC-02.
 *
 * <p>Sobe um contexto proprio com `app.seed.enabled=true`, o que faz o
 * SeedRunner rodar de verdade no boot - e e justamente o boot que o AC-02 mede.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ADMIN_USER=curador-teste", "ADMIN_PASSWORD=senha-de-teste", "app.seed.enabled=true"})
class SeedIT extends AbstractIntegrationTest {

    @Autowired
    private SeedRunner seed;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private TagRepository tags;

    @Autowired
    private ProductContentRepository conteudos;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * O seed do boot deste contexto pode ter sido apagado por outro IT, que trunca
     * as tabelas. Cada teste daqui parte do zero e roda o loader ele mesmo.
     */
    @BeforeEach
    void semearDoZero() {
        jdbc.execute("truncate table category, integration, tag cascade");
        seed.run(null);
    }

    @Test
    @DisplayName("o seed popula categorias, vocabulario e os 20 produtos publicados")
    void popula() {
        assertThat(categorias.count()).isEqualTo(5);
        assertThat(integracoes.count()).isEqualTo(12);
        assertThat(tags.count()).isEqualTo(15);
        assertThat(produtos.count()).isEqualTo(20);

        // Publicados, e nao rascunhos: o catalogo semeado tem de estar no ar (spec 6).
        assertThat(produtos.findAll()).allMatch(produto -> produto.getStatus() == ProductStatus.PUBLISHED);
        assertThat(produtos.findAll()).allMatch(produto -> produto.getPublishedAt() != null);

        // Conteudo curado nos dois idiomas para cada produto - foi o que deixou a
        // publicacao passar pelo validador.
        assertThat(conteudos.count()).isEqualTo(40);
    }

    @Test
    @DisplayName("001-T26: todo produto do seed tem origem, os dois idiomas e uma subcategoria")
    void catalogoRealTemProcedencia() {
        List<Product> todos = produtos.findAll();

        assertThat(todos).hasSize(20);
        assertThat(todos)
                .allMatch(produto -> produto.getSourceNote() != null
                        && !produto.getSourceNote().isBlank()
                        && produto.getSourceNote().contains("coletado em"));

        // Cada uma das quatro subcategorias da spec 6 tem produto. A contagem vem por
        // SQL porque a categoria e lazy e este teste roda fora de transacao.
        Map<String, Integer> porCategoria = new LinkedHashMap<>();
        jdbc.query(
                "select c.slug_pt as slug, count(*) as total from product p"
                        + " join category c on c.id = p.category_id group by c.slug_pt",
                (RowCallbackHandler) linha -> porCategoria.put(linha.getString("slug"), linha.getInt("total")));
        assertThat(porCategoria)
                .containsOnlyKeys(
                        "atendimento-e-chatbots",
                        "automacao-de-whatsapp",
                        "geracao-de-conteudo",
                        "agentes-e-workflows");
        assertThat(porCategoria.values()).allMatch(quantos -> quantos >= 1);

        // Mistura de brasileiros e estrangeiros (spec 6). Pais fica nulo onde a
        // pagina consultada nao deixou claro - null e melhor que chute.
        assertThat(todos).anyMatch(produto -> "BR".equals(produto.getHqCountry()));
        assertThat(todos).anyMatch(produto -> produto.getHqCountry() != null && !"BR".equals(produto.getHqCountry()));

        // Preco so aparece onde a pagina publicou; onde nao publicou, fica nulo e o
        // modelo de cobranca explica o porque.
        assertThat(todos)
                .filteredOn(produto -> produto.getPriceMin() == null)
                .allMatch(produto -> produto.getPricingModel() != PricingModel.SUBSCRIPTION);

        // O catalogo tem ao menos um produto cobrado por unidade, com preco abaixo
        // de um centavo - o caso que motivou a migracao V6.
        assertThat(todos)
                .filteredOn(produto -> produto.getPricingModel() == PricingModel.USAGE_BASED)
                .isNotEmpty()
                .allMatch(produto -> produto.getPriceMin() != null)
                .anyMatch(produto -> produto.getPriceMin().compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    @DisplayName("AC-02: rodar o seed de novo nao duplica nada")
    void naoDuplica() {
        long categoriasAntes = categorias.count();
        long produtosAntes = produtos.count();

        seed.run(null);
        seed.run(null);

        assertThat(categorias.count()).isEqualTo(categoriasAntes);
        assertThat(produtos.count()).isEqualTo(produtosAntes);
        assertThat(integracoes.count()).isEqualTo(12);
        assertThat(tags.count()).isEqualTo(15);
    }

    @Test
    @DisplayName("o catalogo semeado responde pelas rotas publicas")
    void catalogoSemeadoAparece() {
        JsonNode arvore =
                http.getForEntity("/api/v1/categories", JsonNode.class).getBody();
        assertThat(arvore.get("items")).hasSize(1);
        assertThat(arvore.get("items").get(0).get("children")).hasSize(4);
        assertThat(arvore.get("items").get(0).get("product_count").asInt()).isEqualTo(20);

        JsonNode lista = http.getForEntity("/api/v1/products", JsonNode.class).getBody();
        assertThat(lista.get("total").asInt()).isEqualTo(20);

        JsonNode ficha =
                http.getForEntity("/api/v1/products/zendesk", JsonNode.class).getBody();
        assertThat(ficha.get("pros")).isNotEmpty();
        assertThat(ficha.get("category").get("slug").asText()).isEqualTo("atendimento-e-chatbots");

        // O seed alimenta a busca porque passou pelas mesmas escritas do admin.
        JsonNode busca = http.getForEntity("/api/v1/products?q=atendimento", JsonNode.class)
                .getBody();
        assertThat(busca.get("query").get("matched_by").asText()).isEqualTo("FULL_TEXT");
        assertThat(busca.get("items")).isNotEmpty();
    }

    @Test
    @DisplayName("source_note do seed nunca aparece na rota publica")
    void origemNaoVaza() {
        String corpo =
                http.getForEntity("/api/v1/products/zendesk", String.class).getBody();

        assertThat(corpo).doesNotContain("coletado em").doesNotContain("source_note");
    }

    @Test
    @DisplayName("catalogo meio carregado tambem e ignorado, em vez de derrubar o boot")
    void meiaCargaNaoQuebra() {
        // Cenario que quebrou o CI: integracoes gravadas, nenhuma categoria e nenhum
        // produto. A checagem antiga so olhava categoria e produto, dava o banco por
        // vazio e o seed tentava recriar a mesma integracao.
        jdbc.execute("truncate table category, integration, tag cascade");
        integracoes.save(new Integration("whatsapp", "WhatsApp"));

        seed.run(null);

        assertThat(integracoes.count()).isEqualTo(1);
        assertThat(categorias.count()).isZero();
        assertThat(produtos.count()).isZero();
    }
}
