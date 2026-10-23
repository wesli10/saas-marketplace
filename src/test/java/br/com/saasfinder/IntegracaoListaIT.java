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

/** Criterio de verificacao da tarefa 001-T20. */
class IntegracaoListaIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private JdbcTemplate jdbc;

    private Category atendimento;
    private Integration whatsapp;
    private Integration hubspot;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));

        whatsapp = integracoes.save(new Integration("whatsapp", "WhatsApp"));
        hubspot = integracoes.save(new Integration("hubspot", "HubSpot"));
        // Cadastrada e sem nenhum produto: tem de aparecer com zero, nao sumir.
        integracoes.save(new Integration("zendesk", "Zendesk"));
    }

    private Product criar(String slug, ProductStatus status) {
        Product produto = new Product(
                slug,
                "Acme " + slug,
                atendimento,
                "tagline pt",
                "tagline en",
                "descricao pt",
                "description en",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing");
        produto.setStatus(status);
        if (status == ProductStatus.PUBLISHED) {
            produto.setPublishedAt(Instant.now());
        }
        return produtos.saveAndFlush(produto);
    }

    private void vincular(Product produto, Integration integracao) {
        jdbc.update("insert into product_integration values (?, ?)", produto.getId(), integracao.getId());
    }

    private JsonNode listar() {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/integrations", JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return resposta.getBody();
    }

    private long contagemDe(JsonNode corpo, String slug) {
        for (JsonNode item : corpo.get("items")) {
            if (item.get("slug").asText().equals(slug)) {
                return item.get("product_count").asLong();
            }
        }
        throw new AssertionError("integracao ausente na resposta: " + slug);
    }

    @Test
    @DisplayName("a contagem bate com os produtos publicados de cada integracao")
    void contagemPorIntegracao() {
        Product primeiro = criar("acme-chat", ProductStatus.PUBLISHED);
        Product segundo = criar("zeta-desk", ProductStatus.PUBLISHED);
        vincular(primeiro, whatsapp);
        vincular(segundo, whatsapp);
        vincular(segundo, hubspot);

        JsonNode corpo = listar();

        assertThat(contagemDe(corpo, "whatsapp")).isEqualTo(2);
        assertThat(contagemDe(corpo, "hubspot")).isEqualTo(1);
    }

    @Test
    @DisplayName("integracao sem produto publicado aparece com zero, e nao some da lista")
    void semProdutoAparecComZero() {
        Product publicado = criar("acme-chat", ProductStatus.PUBLISHED);
        vincular(publicado, whatsapp);

        JsonNode corpo = listar();

        List<String> slugs = new ArrayList<>();
        corpo.get("items").forEach(item -> slugs.add(item.get("slug").asText()));
        assertThat(slugs).contains("zendesk");
        assertThat(contagemDe(corpo, "zendesk")).isZero();
    }

    @Test
    @DisplayName("produto nao publicado nao entra na contagem")
    void rascunhoNaoConta() {
        Product rascunho = criar("em-rascunho", ProductStatus.DRAFT);
        Product arquivado = criar("arquivado", ProductStatus.ARCHIVED);
        Product publicado = criar("acme-chat", ProductStatus.PUBLISHED);
        vincular(rascunho, whatsapp);
        vincular(arquivado, whatsapp);
        vincular(publicado, whatsapp);

        assertThat(contagemDe(listar(), "whatsapp")).isEqualTo(1);
    }

    @Test
    @DisplayName("a lista sai ordenada por nome e traz slug, name e product_count")
    void formatoEOrdem() {
        JsonNode corpo = listar();

        List<String> nomes = new ArrayList<>();
        corpo.get("items").forEach(item -> nomes.add(item.get("name").asText()));
        assertThat(nomes).containsExactly("HubSpot", "WhatsApp", "Zendesk");

        JsonNode primeiro = corpo.get("items").get(0);
        assertThat(primeiro.get("slug").asText()).isEqualTo("hubspot");
        assertThat(primeiro.has("product_count")).isTrue();
        // Sem locale no envelope: nome de integracao nao se traduz.
        assertThat(corpo.has("locale")).isFalse();
    }

    @Test
    @DisplayName("sem nenhuma integracao cadastrada, a lista vem vazia e nao 404")
    void listaVazia() {
        jdbc.execute("truncate table integration cascade");

        JsonNode corpo = listar();

        assertThat(corpo.get("items")).isEmpty();
    }
}
