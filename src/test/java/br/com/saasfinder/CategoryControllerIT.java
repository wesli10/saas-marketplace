package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T13: AC-03 e AC-04. */
class CategoryControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID raiz;
    private UUID atendimento;
    private UUID whatsapp;

    @BeforeEach
    void prepararCatalogo() {
        jdbc.execute("truncate table category, integration, tag cascade");

        raiz = inserirCategoria("ia-e-automacao", "ai-and-automation", "IA e automacao", "AI and automation", null, 0);
        atendimento = inserirCategoria(
                "atendimento-e-chatbots", "customer-support-and-chatbots", "Atendimento", "Customer support", raiz, 0);
        whatsapp = inserirCategoria(
                "automacao-de-whatsapp",
                "whatsapp-automation",
                "Automacao de WhatsApp",
                "WhatsApp automation",
                raiz,
                1);

        // Dois publicados em atendimento, um em whatsapp, um direto na raiz.
        inserirProduto("acme-chat", atendimento, "PUBLISHED");
        inserirProduto("beta-chat", atendimento, "PUBLISHED");
        inserirProduto("zap-flow", whatsapp, "PUBLISHED");
        inserirProduto("suite-geral", raiz, "PUBLISHED");

        // Rascunho e arquivado nao entram em contagem nenhuma.
        inserirProduto("rascunho", atendimento, "DRAFT");
        inserirProduto("arquivado", atendimento, "ARCHIVED");
    }

    private UUID inserirCategoria(String slugPt, String slugEn, String nomePt, String nomeEn, UUID pai, int ordem) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en, description_pt, description_en,
                                      parent_id, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                slugPt,
                slugEn,
                nomePt,
                nomeEn,
                "descricao em portugues",
                "description in english",
                pai,
                ordem);
        return id;
    }

    private void inserirProduto(String slug, UUID categoria, String status) {
        jdbc.update(
                """
                insert into product (id, slug, category_id, name, tagline_pt, tagline_en, description_pt,
                                     description_en, website_url, pricing_model, source_note, status, published_at)
                values (?, ?, ?, 'Produto', 'tagline pt', 'tagline en', 'descricao pt', 'descricao en',
                        'https://exemplo.com', 'SUBSCRIPTION', 'https://exemplo.com/pricing', ?,
                        case when ? = 'PUBLISHED' then now() else null end)
                """,
                UUID.randomUUID(),
                slug,
                categoria,
                status,
                status);
    }

    @Test
    @DisplayName("AC-03: a arvore em ingles traz nomes em ingles e product_count correto")
    void arvoreEmIngles() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/categories?lang=en", String.class);
        String corpo = resposta.getBody();

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat((String) JsonPath.read(corpo, "$.locale")).isEqualTo("en");
        assertThat((String) JsonPath.read(corpo, "$.items[0].name")).isEqualTo("AI and automation");
        assertThat((String) JsonPath.read(corpo, "$.items[0].slug")).isEqualTo("ai-and-automation");
        assertThat((String) JsonPath.read(corpo, "$.items[0].children[0].name")).isEqualTo("Customer support");

        // Nenhum texto em portugues vaza para a resposta em ingles.
        assertThat(corpo).doesNotContain("Atendimento").doesNotContain("descricao em portugues");
    }

    @Test
    @DisplayName("AC-03: a contagem some com rascunho e arquivado")
    void contagemSoDePublicados() {
        String corpo = http.getForEntity("/api/v1/categories", String.class).getBody();

        // atendimento tem 2 publicados, 1 rascunho e 1 arquivado.
        assertThat((Integer) JsonPath.read(corpo, "$.items[0].children[0].product_count"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("D-02: a raiz soma as subcategorias, e nao so o que esta preso a ela")
    void raizSomaAsFilhas() {
        String corpo = http.getForEntity("/api/v1/categories", String.class).getBody();

        // 1 direto na raiz + 2 em atendimento + 1 em whatsapp.
        assertThat((Integer) JsonPath.read(corpo, "$.items[0].product_count")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(corpo, "$.items[0].children[1].product_count"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("as filhas saem na ordem de sort_order")
    void ordemDasFilhas() {
        String corpo = http.getForEntity("/api/v1/categories", String.class).getBody();

        assertThat((String) JsonPath.read(corpo, "$.items[0].children[0].slug")).isEqualTo("atendimento-e-chatbots");
        assertThat((String) JsonPath.read(corpo, "$.items[0].children[1].slug")).isEqualTo("automacao-de-whatsapp");
    }

    @Test
    @DisplayName("AC-04: o slug em portugues e o em ingles devolvem a mesma categoria")
    void slugNosDoisIdiomas() {
        String porPt = http.getForEntity("/api/v1/categories/atendimento-e-chatbots", String.class)
                .getBody();
        String porEn = http.getForEntity("/api/v1/categories/customer-support-and-chatbots", String.class)
                .getBody();

        assertThat(porPt).isEqualTo(porEn);
        assertThat((String) JsonPath.read(porPt, "$.name")).isEqualTo("Atendimento");
        assertThat((Integer) JsonPath.read(porPt, "$.product_count")).isEqualTo(2);
    }

    @Test
    @DisplayName("a categoria pedida sozinha vem com as filhas dela")
    void categoriaTrazAsFilhas() {
        String corpo = http.getForEntity("/api/v1/categories/ia-e-automacao", String.class)
                .getBody();

        assertThat((String) JsonPath.read(corpo, "$.slug")).isEqualTo("ia-e-automacao");
        assertThat((java.util.List<?>) JsonPath.read(corpo, "$.children")).hasSize(2);
    }

    @Test
    @DisplayName("slug inexistente responde 404 em problem+json nomeando o slug")
    void slugInexistente() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/categories/nao-existe", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(resposta.getHeaders().getContentType())
                .isNotNull()
                .matches(tipo -> tipo.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(resposta.getBody()).contains("/problems/not-found").contains("nao-existe");
    }

    @Test
    @DisplayName("os campos saem em snake_case e a resposta declara o idioma")
    void formatoDaResposta() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/categories?lang=en", String.class);

        assertThat(resposta.getBody()).contains("\"product_count\"").contains("\"sort_order\"");
        assertThat(resposta.getBody()).doesNotContain("productCount").doesNotContain("sortOrder");
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en");
    }

    @Test
    @DisplayName("categoria sem produto publicado aparece com zero, e nao sumida")
    void categoriaVaziaApareceComZero() {
        jdbc.execute("delete from product");

        String corpo = http.getForEntity("/api/v1/categories", String.class).getBody();

        assertThat((Integer) JsonPath.read(corpo, "$.items[0].product_count")).isZero();
        assertThat((Integer) JsonPath.read(corpo, "$.items[0].children[0].product_count"))
                .isZero();
    }
}
