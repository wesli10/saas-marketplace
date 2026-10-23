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

/** Criterio de verificacao da tarefa 001-T19: AC-16. */
class ProdutoComparacaoIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private JdbcTemplate jdbc;

    private Category atendimento;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));

        criar("acme-chat", "Acme Chat", ProductStatus.PUBLISHED);
        criar("zeta-desk", "Zeta Desk", ProductStatus.PUBLISHED);
        criar("omega-bot", "Omega Bot", ProductStatus.PUBLISHED);
        criar("em-rascunho", "Rascunho", ProductStatus.DRAFT);
    }

    private void criar(String slug, String nome, ProductStatus status) {
        Product produto = new Product(
                slug,
                nome,
                atendimento,
                "atendimento ao cliente",
                "customer support",
                "descricao pt",
                "description en",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing");
        produto.setStatus(status);
        if (status == ProductStatus.PUBLISHED) {
            produto.setPublishedAt(Instant.now());
        }
        produtos.saveAndFlush(produto);
    }

    private ResponseEntity<JsonNode> comparar(String consulta) {
        return http.getForEntity("/api/v1/products/compare" + consulta, JsonNode.class);
    }

    private List<String> slugs(JsonNode corpo) {
        List<String> encontrados = new ArrayList<>();
        corpo.get("items").forEach(item -> encontrados.add(item.get("slug").asText()));
        return encontrados;
    }

    @Test
    @DisplayName("AC-16: tres slugs devolvem tres fichas na ordem pedida")
    void tresFichasNaOrdemPedida() {
        JsonNode corpo = comparar("?slugs=omega-bot,acme-chat,zeta-desk").getBody();

        assertThat(slugs(corpo)).containsExactly("omega-bot", "acme-chat", "zeta-desk");
        assertThat(corpo.get("locale").asText()).isEqualTo("pt-BR");

        // Mesma ficha da rota individual, e nao um resumo.
        JsonNode primeira = corpo.get("items").get(0);
        assertThat(primeira.get("description").asText()).isEqualTo("descricao pt");
        assertThat(primeira.get("category").get("slug").asText()).isEqualTo("atendimento");
        assertThat(primeira.has("pros")).isTrue();
        assertThat(primeira.has("faq")).isTrue();
    }

    @Test
    @DisplayName("a ordem pedida e respeitada mesmo invertida")
    void ordemAcompanhaOPedido() {
        assertThat(slugs(comparar("?slugs=acme-chat,zeta-desk").getBody())).containsExactly("acme-chat", "zeta-desk");
        assertThat(slugs(comparar("?slugs=zeta-desk,acme-chat").getBody())).containsExactly("zeta-desk", "acme-chat");
    }

    @Test
    @DisplayName("AC-16: um slug so responde 400")
    void umSlugNaoCompara() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/products/compare?slugs=acme-chat", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("/problems/invalid-parameter").contains("slugs");
    }

    @Test
    @DisplayName("AC-16: quatro slugs respondem 400")
    void quatroSlugsNaoComparam() {
        ResponseEntity<String> resposta = http.getForEntity(
                "/api/v1/products/compare?slugs=acme-chat,zeta-desk,omega-bot,acme-chat", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).contains("/problems/invalid-parameter");
    }

    @Test
    @DisplayName("sem o parametro slugs, tambem e 400")
    void semSlugs() {
        assertThat(http.getForEntity("/api/v1/products/compare", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("AC-16: slug inexistente responde 404 dizendo qual faltou")
    void slugInexistenteNomeadoNoErro() {
        ResponseEntity<JsonNode> resposta = comparar("?slugs=acme-chat,nao-existe");

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(resposta.getHeaders().getContentType().toString()).startsWith("application/problem+json");

        JsonNode corpo = resposta.getBody();
        assertThat(corpo.get("detail").asText()).contains("nao-existe");

        List<String> ausentes = new ArrayList<>();
        corpo.get("missing_slugs").forEach(item -> ausentes.add(item.asText()));
        assertThat(ausentes).containsExactly("nao-existe");
    }

    @Test
    @DisplayName("missing_slugs lista todos os que faltaram, nao so o primeiro")
    void todosOsAusentesNoErro() {
        JsonNode corpo = comparar("?slugs=nem-esse,acme-chat,nem-aquele").getBody();

        List<String> ausentes = new ArrayList<>();
        corpo.get("missing_slugs").forEach(item -> ausentes.add(item.asText()));
        assertThat(ausentes).containsExactly("nem-esse", "nem-aquele");
    }

    @Test
    @DisplayName("AC-16: produto em DRAFT conta como inexistente na comparacao")
    void rascunhoContaComoAusente() {
        ResponseEntity<JsonNode> resposta = comparar("?slugs=acme-chat,em-rascunho");

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);

        List<String> ausentes = new ArrayList<>();
        resposta.getBody().get("missing_slugs").forEach(item -> ausentes.add(item.asText()));
        assertThat(ausentes).containsExactly("em-rascunho");
    }

    @Test
    @DisplayName("a comparacao tambem responde no idioma pedido")
    void comparacaoEmIngles() {
        JsonNode corpo = comparar("?slugs=acme-chat,zeta-desk&lang=en").getBody();

        assertThat(corpo.get("locale").asText()).isEqualTo("en");
        assertThat(corpo.get("items").get(0).get("tagline").asText()).isEqualTo("customer support");
        assertThat(corpo.get("items").get(0).get("category").get("slug").asText())
                .isEqualTo("customer-support");
    }

    @Test
    @DisplayName("comparar nao rouba a rota da ficha: /products/compare e /products/{slug} convivem")
    void rotaCompareNaoColideComFicha() {
        // Se o Spring casasse "compare" com {slug}, isto viria 404 em vez de 400.
        assertThat(http.getForEntity("/api/v1/products/compare?slugs=acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(http.getForEntity("/api/v1/products/acme-chat", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
    }
}
