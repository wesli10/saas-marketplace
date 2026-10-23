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
import java.math.BigDecimal;
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

/** Criterio de verificacao da tarefa 001-T15: AC-13 e AC-14. */
class ProdutoFiltroIT extends AbstractIntegrationTest {

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

    private Category raiz;
    private Category atendimento;
    private Category conteudo;
    private Integration whatsapp;
    private Integration hubspot;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");

        raiz = categorias.save(new Category("ia-e-automacao", "ai-and-automation", "IA", "AI", null));
        atendimento = categorias.save(new Category("atendimento", "customer-support", "Atendimento", "Support", raiz));
        conteudo = categorias.save(new Category("conteudo", "content", "Conteudo", "Content", raiz));

        whatsapp = integracoes.save(new Integration("whatsapp", "WhatsApp"));
        hubspot = integracoes.save(new Integration("hubspot", "HubSpot"));
    }

    private Product criar(String slug, Category categoria, PricingModel modelo) {
        Product produto = new Product(
                slug,
                "Acme " + slug,
                categoria,
                "tagline pt",
                "tagline en",
                "descricao pt",
                "description en",
                "https://acme.com",
                modelo,
                "https://acme.com/pricing");
        produto.setStatus(ProductStatus.PUBLISHED);
        produto.setPublishedAt(Instant.now());
        return produtos.saveAndFlush(produto);
    }

    private void vincular(Product produto, Integration integracao) {
        jdbc.update("insert into product_integration values (?, ?)", produto.getId(), integracao.getId());
    }

    private List<String> slugs(String consulta) {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products" + consulta, JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        List<String> encontrados = new ArrayList<>();
        resposta.getBody()
                .get("items")
                .forEach(item -> encontrados.add(item.get("slug").asText()));
        return encontrados;
    }

    @Test
    @DisplayName("AC-13: os filtros se combinam, e so passa quem satisfaz todos")
    void filtrosCombinam() {
        Product completo = criar("completo", atendimento, PricingModel.SUBSCRIPTION);
        completo.setHasFreeTrial(true);
        produtos.saveAndFlush(completo);
        vincular(completo, whatsapp);

        Product semTeste = criar("sem-teste", atendimento, PricingModel.SUBSCRIPTION);
        vincular(semTeste, whatsapp);

        Product outraCategoria = criar("outra-categoria", conteudo, PricingModel.SUBSCRIPTION);
        outraCategoria.setHasFreeTrial(true);
        produtos.saveAndFlush(outraCategoria);
        vincular(outraCategoria, whatsapp);

        Product semIntegracao = criar("sem-integracao", atendimento, PricingModel.SUBSCRIPTION);
        semIntegracao.setHasFreeTrial(true);
        produtos.saveAndFlush(semIntegracao);

        assertThat(slugs("?category=atendimento&integration=whatsapp&has_free_trial=true"))
                .containsExactly("completo");
    }

    @Test
    @DisplayName("AC-13: duas integracoes exigem as duas, nao qualquer uma")
    void duasIntegracoesExigemAsDuas() {
        Product ambas = criar("ambas", atendimento, PricingModel.SUBSCRIPTION);
        vincular(ambas, whatsapp);
        vincular(ambas, hubspot);

        Product soUma = criar("so-uma", atendimento, PricingModel.SUBSCRIPTION);
        vincular(soUma, whatsapp);

        assertThat(slugs("?integration=whatsapp&integration=hubspot")).containsExactly("ambas");
        assertThat(slugs("?integration=whatsapp")).containsExactlyInAnyOrder("ambas", "so-uma");
    }

    @Test
    @DisplayName("AC-13: dois modelos de cobranca aceitam qualquer um dos dois")
    void doisModelosAceitamQualquerUm() {
        criar("gratis", atendimento, PricingModel.FREE);
        criar("freemium", atendimento, PricingModel.FREEMIUM);
        criar("assinatura", atendimento, PricingModel.SUBSCRIPTION);

        assertThat(slugs("?pricing_model=FREE&pricing_model=FREEMIUM")).containsExactlyInAnyOrder("gratis", "freemium");
    }

    @Test
    @DisplayName("AC-13: filtrar pela categoria pai traz os produtos das filhas")
    void categoriaPaiIncluiFilhas() {
        criar("de-atendimento", atendimento, PricingModel.SUBSCRIPTION);
        criar("de-conteudo", conteudo, PricingModel.SUBSCRIPTION);

        assertThat(slugs("?category=ia-e-automacao")).containsExactlyInAnyOrder("de-atendimento", "de-conteudo");
        assertThat(slugs("?category=atendimento")).containsExactly("de-atendimento");
    }

    @Test
    @DisplayName("AC-13: a categoria pode ser filtrada pelo slug em ingles")
    void categoriaAceitaSlugEmIngles() {
        criar("de-atendimento", atendimento, PricingModel.SUBSCRIPTION);

        assertThat(slugs("?category=customer-support")).containsExactly("de-atendimento");
    }

    @Test
    @DisplayName("AC-14: price_max exclui quem passa do teto e quem nao tem preco divulgado")
    void precoMaximo() {
        Product barato = criar("barato", atendimento, PricingModel.SUBSCRIPTION);
        barato.setPriceMin(new BigDecimal("50.00"));
        barato.setPriceCurrency("BRL");
        produtos.saveAndFlush(barato);

        Product caro = criar("caro", atendimento, PricingModel.SUBSCRIPTION);
        caro.setPriceMin(new BigDecimal("150.00"));
        caro.setPriceCurrency("BRL");
        produtos.saveAndFlush(caro);

        // Sem preco divulgado: nao da para afirmar que cabe no teto.
        criar("sob-consulta", atendimento, PricingModel.CONTACT_SALES);

        assertThat(slugs("?price_max=100")).containsExactly("barato");
    }

    @Test
    @DisplayName("AC-14: o teto e inclusivo")
    void tetoInclusivo() {
        Product noLimite = criar("no-limite", atendimento, PricingModel.SUBSCRIPTION);
        noLimite.setPriceMin(new BigDecimal("100.00"));
        noLimite.setPriceCurrency("BRL");
        produtos.saveAndFlush(noLimite);

        assertThat(slugs("?price_max=100")).containsExactly("no-limite");
    }

    @Test
    @DisplayName("o eco devolve exatamente os filtros aplicados")
    void ecoDosFiltros() {
        ResponseEntity<JsonNode> resposta = http.getForEntity(
                "/api/v1/products?category=atendimento&integration=whatsapp&pricing_model=FREE&has_free_trial=true&price_max=100",
                JsonNode.class);

        JsonNode eco = resposta.getBody().get("query");
        assertThat(eco.get("category").asText()).isEqualTo("atendimento");
        assertThat(eco.get("integration").get(0).asText()).isEqualTo("whatsapp");
        assertThat(eco.get("pricing_model").get(0).asText()).isEqualTo("FREE");
        assertThat(eco.get("has_free_trial").asBoolean()).isTrue();
        assertThat(eco.get("price_max").asInt()).isEqualTo(100);
        assertThat(resposta.getBody().get("locale").asText()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("filtro sem nada informado nao restringe")
    void ecoVazioNaoRestringe() {
        criar("qualquer", atendimento, PricingModel.SUBSCRIPTION);

        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products", JsonNode.class);

        assertThat(resposta.getBody().get("items")).hasSize(1);
        assertThat(resposta.getBody().get("query").get("category").isNull()).isTrue();
        assertThat(resposta.getBody().get("query").get("integration")).isEmpty();
    }

    @Test
    @DisplayName("categoria inexistente e modelo desconhecido viram 400, nao lista vazia")
    void filtroInvalidoVira400() {
        ResponseEntity<String> categoria = http.getForEntity("/api/v1/products?category=nao-existe", String.class);
        assertThat(categoria.getStatusCode().value()).isEqualTo(400);
        assertThat(categoria.getBody()).contains("/problems/invalid-parameter").contains("category");

        ResponseEntity<String> modelo = http.getForEntity("/api/v1/products?pricing_model=DE_GRACA", String.class);
        assertThat(modelo.getStatusCode().value()).isEqualTo(400);
        assertThat(modelo.getBody()).contains("pricing_model");
    }
}
