package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.PricePeriod;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.domain.Tag;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T18: AC-06 e AC-07. */
class ProdutoFichaIT extends AbstractIntegrationTest {

    /** Vai para o source_note do produto e nunca pode aparecer numa resposta publica. */
    private static final String ORIGEM_DO_DADO = "https://acme.com/pricing - coletado em 2026-08-20";

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
    private Product acme;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));

        acme = new Product(
                "acme-chat",
                "Acme Chat",
                atendimento,
                "atendimento ao cliente no whatsapp",
                "customer support on whatsapp",
                "Plataforma brasileira de atendimento com chatbot.",
                "Brazilian customer support platform with chatbot.",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                ORIGEM_DO_DADO);
        acme.setStatus(ProductStatus.PUBLISHED);
        acme.setPublishedAt(Instant.now());
        acme.setLogoUrl("https://acme.com/logo.png");
        acme.setPriceMin(new BigDecimal("99.00"));
        acme.setPriceCurrency("BRL");
        acme.setPricePeriod(PricePeriod.MONTH);
        acme.setHasFreeTrial(true);
        acme.setSupportedLanguages(List.of("pt", "en"));
        acme.setHqCountry("BR");
        acme = produtos.saveAndFlush(acme);

        Integration whatsapp = integracoes.save(new Integration("whatsapp", "WhatsApp"));
        jdbc.update("insert into product_integration values (?, ?)", acme.getId(), whatsapp.getId());

        Tag chatbot = tags.save(new Tag("chatbot", "Chatbot", "Chatbot"));
        Tag atendimentoTag = tags.save(new Tag("atendimento", "Atendimento", "Customer support"));
        jdbc.update("insert into product_tag values (?, ?)", acme.getId(), chatbot.getId());
        jdbc.update("insert into product_tag values (?, ?)", acme.getId(), atendimentoTag.getId());
    }

    private void curar(String locale, List<String> pros, List<String> cons, String melhorPara, String faqJson) {
        jdbc.update(
                "insert into product_content (product_id, locale, pros, cons, best_for, faq)"
                        + " values (?, ?, ?, ?, ?, ?::jsonb)",
                acme.getId(),
                locale,
                pros.toArray(String[]::new),
                cons.toArray(String[]::new),
                melhorPara,
                faqJson);
    }

    private ResponseEntity<String> pedirTexto(String caminho) {
        return http.getForEntity("/api/v1/products" + caminho, String.class);
    }

    private JsonNode ficha(String caminho) {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products" + caminho, JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return resposta.getBody();
    }

    /** Nomes de uma lista de {slug, name}, na ordem em que a ficha devolveu. */
    private List<String> nomes(JsonNode lista) {
        List<String> valores = new ArrayList<>();
        lista.forEach(item -> valores.add(item.get("name").asText()));
        return valores;
    }

    private List<String> textos(JsonNode lista) {
        List<String> valores = new ArrayList<>();
        lista.forEach(item -> valores.add(item.asText()));
        return valores;
    }

    @Test
    @DisplayName("a ficha traz o produto inteiro no idioma padrao")
    void fichaCompletaEmPortugues() {
        curar(
                "pt-BR",
                List.of("integra com whatsapp", "suporte em portugues"),
                List.of("sem plano gratuito"),
                "Pequenas equipes de atendimento",
                "[{\"question\":\"Tem teste gratis?\",\"answer\":\"Sim, 14 dias.\"}]");

        JsonNode ficha = ficha("/acme-chat");

        assertThat(ficha.get("slug").asText()).isEqualTo("acme-chat");
        assertThat(ficha.get("locale").asText()).isEqualTo("pt-BR");
        assertThat(ficha.get("name").asText()).isEqualTo("Acme Chat");
        assertThat(ficha.get("tagline").asText()).isEqualTo("atendimento ao cliente no whatsapp");
        assertThat(ficha.get("description").asText()).contains("Plataforma brasileira");
        assertThat(ficha.get("website_url").asText()).isEqualTo("https://acme.com");
        assertThat(ficha.get("logo_url").asText()).isEqualTo("https://acme.com/logo.png");
        assertThat(ficha.get("category").get("slug").asText()).isEqualTo("atendimento");
        assertThat(ficha.get("category").get("name").asText()).isEqualTo("Atendimento");
        assertThat(ficha.get("pricing_model").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(ficha.get("price_min").decimalValue()).isEqualByComparingTo("99.00");
        assertThat(ficha.get("price_currency").asText()).isEqualTo("BRL");
        assertThat(ficha.get("price_period").asText()).isEqualTo("MONTH");
        assertThat(ficha.get("has_free_trial").asBoolean()).isTrue();
        assertThat(ficha.get("has_free_plan").asBoolean()).isFalse();
        assertThat(textos(ficha.get("supported_languages"))).containsExactly("pt", "en");
        assertThat(ficha.get("hq_country").asText()).isEqualTo("BR");
        assertThat(ficha.get("published_at").isNull()).isFalse();
        assertThat(ficha.get("updated_at").isNull()).isFalse();

        assertThat(ficha.get("integrations")).hasSize(1);
        assertThat(ficha.get("integrations").get(0).get("name").asText()).isEqualTo("WhatsApp");

        // Ordem alfabetica: Set nao tem ordem propria e a ficha precisa ser estavel.
        assertThat(nomes(ficha.get("tags"))).containsExactly("Atendimento", "Chatbot");

        assertThat(textos(ficha.get("pros"))).containsExactly("integra com whatsapp", "suporte em portugues");
        assertThat(textos(ficha.get("cons"))).containsExactly("sem plano gratuito");
        assertThat(ficha.get("best_for").asText()).isEqualTo("Pequenas equipes de atendimento");
        assertThat(ficha.get("faq")).hasSize(1);
        assertThat(ficha.get("faq").get(0).get("question").asText()).isEqualTo("Tem teste gratis?");
    }

    @Test
    @DisplayName("AC-07: ?lang=en devolve locale en e nenhum texto em portugues")
    void fichaEmIngles() {
        curar("pt-BR", List.of("integra com whatsapp"), List.of(), "Equipes brasileiras", "[]");
        curar("en", List.of("integrates with whatsapp"), List.of(), "Small support teams", "[]");

        String corpo = pedirTexto("/acme-chat?lang=en").getBody();
        JsonNode ficha = ficha("/acme-chat?lang=en");

        assertThat(ficha.get("locale").asText()).isEqualTo("en");
        assertThat(ficha.get("tagline").asText()).isEqualTo("customer support on whatsapp");
        assertThat(ficha.get("description").asText()).contains("Brazilian customer support");
        assertThat(ficha.get("category").get("slug").asText()).isEqualTo("customer-support");
        assertThat(ficha.get("category").get("name").asText()).isEqualTo("Customer support");
        assertThat(textos(ficha.get("pros"))).containsExactly("integrates with whatsapp");
        assertThat(ficha.get("best_for").asText()).isEqualTo("Small support teams");
        assertThat(nomes(ficha.get("tags"))).containsExactly("Chatbot", "Customer support");

        // O corpo inteiro, e nao campo a campo: um texto em portugues esquecido em
        // qualquer canto da ficha reprova o teste.
        assertThat(corpo)
                .doesNotContain("atendimento ao cliente")
                .doesNotContain("Plataforma brasileira")
                .doesNotContain("integra com whatsapp")
                .doesNotContain("Equipes brasileiras");
    }

    @Test
    @DisplayName("AC-06: produto em DRAFT responde 404 em problem+json")
    void rascunhoNaoAparece() {
        acme.setStatus(ProductStatus.DRAFT);
        produtos.saveAndFlush(acme);

        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products/acme-chat", JsonNode.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(resposta.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(resposta.getBody().get("title").asText()).isEqualTo("Recurso nao encontrado");
        assertThat(resposta.getBody().get("detail").asText()).contains("acme-chat");
    }

    @Test
    @DisplayName("AC-06: produto arquivado tambem some da rota publica")
    void arquivadoNaoAparece() {
        acme.setStatus(ProductStatus.ARCHIVED);
        produtos.saveAndFlush(acme);

        assertThat(pedirTexto("/acme-chat").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("slug inexistente responde 404, e nao 500")
    void slugDesconhecido() {
        assertThat(pedirTexto("/nao-existe").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("source_note nunca sai na resposta publica")
    void origemDoDadoNaoVaza() {
        curar("pt-BR", List.of("integra com whatsapp"), List.of(), "Equipes", "[]");

        String corpo = pedirTexto("/acme-chat").getBody();

        assertThat(corpo).doesNotContain(ORIGEM_DO_DADO).doesNotContain("source_note");
    }

    @Test
    @DisplayName("sem conteudo curado no idioma pedido, os campos longos saem vazios e sem 500")
    void semConteudoNoIdioma() {
        curar("pt-BR", List.of("integra com whatsapp"), List.of("sem plano gratuito"), "Equipes", "[]");

        // Nenhum product_content em `en`: a ficha curta ainda tem de responder.
        JsonNode ficha = ficha("/acme-chat?lang=en");

        assertThat(ficha.get("locale").asText()).isEqualTo("en");
        assertThat(ficha.get("tagline").asText()).isEqualTo("customer support on whatsapp");
        assertThat(ficha.get("pros")).isEmpty();
        assertThat(ficha.get("cons")).isEmpty();
        assertThat(ficha.get("best_for").isNull()).isTrue();
        assertThat(ficha.get("faq")).isEmpty();
    }

    @Test
    @DisplayName("AC-08: o header Accept-Language vale para a ficha, e o lang vence o header")
    void idiomaVemDoHeaderOuDoParametro() {
        HttpHeaders emIngles = new HttpHeaders();
        emIngles.set(HttpHeaders.ACCEPT_LANGUAGE, "en");

        ResponseEntity<JsonNode> peloHeader = http.exchange(
                "/api/v1/products/acme-chat",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(emIngles),
                JsonNode.class);
        assertThat(peloHeader.getBody().get("locale").asText()).isEqualTo("en");
        assertThat(peloHeader.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE))
                .isEqualTo("en");

        ResponseEntity<JsonNode> parametroVence = http.exchange(
                "/api/v1/products/acme-chat?lang=pt-BR",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(emIngles),
                JsonNode.class);
        assertThat(parametroVence.getBody().get("locale").asText()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("AC-09: lang invalido na ficha responde 400")
    void idiomaInvalido() {
        assertThat(pedirTexto("/acme-chat?lang=fr").getStatusCode().value()).isEqualTo(400);
    }
}
