package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T06: invariantes de produto e conteudo no banco. */
class ProdutoSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID categoria;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category cascade");
        categoria = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en)
                values (?, 'atendimento', 'customer-support', 'Atendimento', 'Customer support')
                """,
                categoria);
    }

    /**
     * Produto minimo e valido. Cada teste sobrescreve a coluna que quer exercitar, em vez
     * de acrescentar coluna - o que duplicaria as que ja estao no conjunto base.
     */
    private Map<String, Object> produtoValido(String slug) {
        Map<String, Object> colunas = new LinkedHashMap<>();
        colunas.put("id", UUID.randomUUID());
        colunas.put("slug", slug);
        colunas.put("category_id", categoria);
        colunas.put("name", "Acme");
        colunas.put("tagline_pt", "tagline pt");
        colunas.put("tagline_en", "tagline en");
        colunas.put("description_pt", "descricao pt");
        colunas.put("description_en", "descricao en");
        colunas.put("website_url", "https://acme.com");
        colunas.put("pricing_model", "SUBSCRIPTION");
        colunas.put("source_note", "https://acme.com/pricing - 2026-08-26");
        return colunas;
    }

    /** Colunas de array e jsonb precisam de cast explicito: o driver manda texto. */
    private static String placeholder(String coluna) {
        return switch (coluna) {
            case "supported_languages", "pros", "cons" -> "?::text[]";
            case "faq" -> "?::jsonb";
            default -> "?";
        };
    }

    private UUID inserirProduto(Map<String, Object> colunas) {
        String nomes = String.join(", ", colunas.keySet());
        String valores =
                colunas.keySet().stream().map(ProdutoSchemaIT::placeholder).collect(Collectors.joining(", "));

        jdbc.update(
                "insert into product (%s) values (%s)".formatted(nomes, valores),
                colunas.values().toArray());
        return (UUID) colunas.get("id");
    }

    private UUID inserirProduto(String slug) {
        return inserirProduto(produtoValido(slug));
    }

    private Map<String, Object> conteudo(UUID produto, String locale) {
        Map<String, Object> colunas = new LinkedHashMap<>();
        colunas.put("product_id", produto);
        colunas.put("locale", locale);
        return colunas;
    }

    private void inserirConteudo(Map<String, Object> colunas) {
        String nomes = String.join(", ", colunas.keySet());
        String valores =
                colunas.keySet().stream().map(ProdutoSchemaIT::placeholder).collect(Collectors.joining(", "));

        jdbc.update(
                "insert into product_content (%s) values (%s)".formatted(nomes, valores),
                colunas.values().toArray());
    }

    // --- os quatro criterios escritos no tasks.md ---

    @Test
    @DisplayName("rascunho sem preco entra normalmente (desvio D-04)")
    void rascunhoSemPrecoEhAceito() {
        assertThatCode(() -> inserirProduto("acme-chat")).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("select status from product where slug = 'acme-chat'", String.class))
                .isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("preco sem moeda e recusado")
    void precoSemMoedaEhRecusado() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("price_min", new BigDecimal("99.00"));

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_preco_exige_moeda");
    }

    @Test
    @DisplayName("status fora do enum e recusado")
    void statusInvalidoEhRecusado() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("status", "PUBLICADO");

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_status_valido");
    }

    @Test
    @DisplayName("slug duplicado e recusado")
    void slugDuplicadoEhRecusado() {
        inserirProduto("acme-chat");

        assertThatThrownBy(() -> inserirProduto("acme-chat"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_slug_uq");
    }

    // --- demais invariantes da spec secao 4.2 ---

    @Test
    @DisplayName("pricing_model fora do enum e recusado")
    void pricingModelInvalidoEhRecusado() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("pricing_model", "GRATIS");

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_pricing_model_valido");
    }

    @Test
    @DisplayName("publicado sem published_at e recusado")
    void publicadoPrecisaDeData() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("status", "PUBLISHED");

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_publicado_tem_data");
    }

    @Test
    @DisplayName("slug de produto publicado e imutavel; em rascunho pode mudar")
    void slugImutavelDepoisDePublicado() {
        UUID id = inserirProduto("acme-chat");

        assertThatCode(() -> jdbc.update("update product set slug = 'acme-chat-v2' where id = ?", id))
                .doesNotThrowAnyException();

        jdbc.update("update product set status = 'PUBLISHED', published_at = now() where id = ?", id);

        assertThatThrownBy(() -> jdbc.update("update product set slug = 'outro-slug' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("slug de produto publicado e imutavel");
    }

    @Test
    @DisplayName("moeda fora do ISO 4217 e recusada")
    void moedaForaDoPadrao() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("price_min", new BigDecimal("10.00"));
        produto.put("price_currency", "brl");

        assertThatThrownBy(() -> inserirProduto(produto)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("pais fora do ISO 3166-1 alpha-2 e recusado")
    void paisForaDoPadrao() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("hq_country", "br");

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_hq_country_iso3166");
    }

    @Test
    @DisplayName("idioma fora do ISO 639-1 e recusado")
    void idiomaForaDoPadrao() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("supported_languages", "{portugues}");

        assertThatThrownBy(() -> inserirProduto(produto))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_idiomas_iso639");
    }

    @Test
    @DisplayName("lista de idiomas vazia passa em rascunho: a exigencia de ao menos um e da publicacao")
    void listaDeIdiomasVaziaEhAceitaEmRascunho() {
        Map<String, Object> produto = produtoValido("acme-chat");
        produto.put("supported_languages", "{}");

        assertThatCode(() -> inserirProduto(produto)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("apagar categoria com produto vinculado e recusado (AC-21)")
    void categoriaComProdutoNaoEhApagada() {
        inserirProduto("acme-chat");

        assertThatThrownBy(() -> jdbc.update("delete from category where id = ?", categoria))
                .isInstanceOf(DataAccessException.class);
    }

    // --- product_content, spec secao 4.3 ---

    @Test
    @DisplayName("locale de conteudo fora de pt-BR e en e recusado")
    void localeDeConteudoInvalido() {
        UUID id = inserirProduto("acme-chat");

        assertThatThrownBy(() -> inserirConteudo(conteudo(id, "fr")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_content_locale_valido");
    }

    @Test
    @DisplayName("mais de 8 pros e recusado")
    void limiteDeQuantidadeDePros() {
        UUID id = inserirProduto("acme-chat");
        Map<String, Object> linha = conteudo(id, "pt-BR");
        linha.put("pros", "{a,b,c,d,e,f,g,h,i}");

        assertThatThrownBy(() -> inserirConteudo(linha))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_content_pros_limite");
    }

    @Test
    @DisplayName("pro acima de 200 caracteres e recusado")
    void limiteDeTamanhoDePro() {
        UUID id = inserirProduto("acme-chat");
        Map<String, Object> linha = conteudo(id, "pt-BR");
        linha.put("pros", "{" + "x".repeat(201) + "}");

        assertThatThrownBy(() -> inserirConteudo(linha))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_content_pros_limite");
    }

    @Test
    @DisplayName("faq com mais de 10 perguntas e recusada")
    void limiteDeFaq() {
        UUID id = inserirProduto("acme-chat");
        Map<String, Object> linha = conteudo(id, "pt-BR");
        linha.put(
                "faq",
                "[" + "{\"question\":\"q\",\"answer\":\"a\"},".repeat(10) + "{\"question\":\"q\",\"answer\":\"a\"}]");

        assertThatThrownBy(() -> inserirConteudo(linha))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_content_faq_limite");
    }

    @Test
    @DisplayName("faq que nao e array e recusada")
    void faqPrecisaSerArray() {
        UUID id = inserirProduto("acme-chat");
        Map<String, Object> linha = conteudo(id, "pt-BR");
        linha.put("faq", "{\"question\":\"q\"}");

        assertThatThrownBy(() -> inserirConteudo(linha))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_content_faq_array");
    }

    @Test
    @DisplayName("o mesmo idioma nao entra duas vezes para o mesmo produto")
    void conteudoUnicoPorIdioma() {
        UUID id = inserirProduto("acme-chat");
        inserirConteudo(conteudo(id, "pt-BR"));

        assertThatThrownBy(() -> inserirConteudo(conteudo(id, "pt-BR"))).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("apagar o produto leva junto o conteudo dos dois idiomas")
    void conteudoCaiComOProduto() {
        UUID id = inserirProduto("acme-chat");
        inserirConteudo(conteudo(id, "pt-BR"));
        inserirConteudo(conteudo(id, "en"));

        jdbc.update("delete from product where id = ?", id);

        assertThat(jdbc.queryForObject("select count(*) from product_content where product_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("V6: preco por unidade abaixo de um centavo sobrevive ao ida e volta")
    void precoPorUnidadeNaoEhArredondado() {
        Map<String, Object> produto = produtoValido("twilio-whatsapp");
        produto.put("pricing_model", "USAGE_BASED");
        // US$ 0,005 por mensagem: com as duas casas originais isso virava 0,01,
        // o dobro do preco real. Foi o T26 que esbarrou nisso.
        produto.put("price_min", new BigDecimal("0.0050"));
        produto.put("price_currency", "USD");
        inserirProduto(produto);

        BigDecimal gravado =
                jdbc.queryForObject("select price_min from product where slug = 'twilio-whatsapp'", BigDecimal.class);

        assertThat(gravado).isEqualByComparingTo("0.0050");
        assertThat(gravado.compareTo(new BigDecimal("0.01"))).isNegative();
    }

    @Test
    @DisplayName("V6: o filtro por preco continua enxergando o valor com quatro casas")
    void filtroDePrecoEnxergaAsQuatroCasas() {
        Map<String, Object> barato = produtoValido("por-unidade");
        barato.put("price_min", new BigDecimal("0.0050"));
        barato.put("price_currency", "USD");
        inserirProduto(barato);

        Map<String, Object> caro = produtoValido("por-assinatura");
        caro.put("price_min", new BigDecimal("99.00"));
        caro.put("price_currency", "USD");
        inserirProduto(caro);

        assertThat(jdbc.queryForList(
                        "select slug from product where price_min is not null and price_min <= 1 order by slug",
                        String.class))
                .containsExactly("por-unidade");
    }
}
