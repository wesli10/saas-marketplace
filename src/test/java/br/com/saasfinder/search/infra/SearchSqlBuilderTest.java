package br.com.saasfinder.search.infra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.search.domain.ProductSearchQuery;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste unitario do montador de SQL: a combinacao de filtros e onde mora o risco,
 * e da para verificar sem banco. Os efeitos no resultado ficam no ProdutoFiltroIT.
 */
@SuppressWarnings("unchecked")
class SearchSqlBuilderTest {

    private final SearchSqlBuilder builder = new SearchSqlBuilder();

    private ProductSearchQuery consulta(
            Set<UUID> categorias,
            List<String> integracoes,
            List<String> modelos,
            Boolean teste,
            Boolean plano,
            BigDecimal precoMaximo) {
        return new ProductSearchQuery(
                AppLocale.PT_BR,
                new PagingParams(0, 20),
                null,
                categorias,
                integracoes,
                modelos,
                teste,
                plano,
                precoMaximo);
    }

    @Test
    @DisplayName("sem filtro, so a clausula de publicado")
    void semFiltro() {
        String sql = builder.montar(consulta(Set.of(), List.of(), List.of(), null, null, null))
                .sql();

        assertThat(sql)
                .contains("p.status = 'PUBLISHED'")
                .doesNotContain("category_id in")
                .doesNotContain("exists (");
    }

    @Test
    @DisplayName("duas integracoes viram dois EXISTS, e nao um IN")
    void integracoesSaoCombinadasComE() {
        var montada = builder.montar(consulta(Set.of(), List.of("whatsapp", "hubspot"), List.of(), null, null, null));

        // Um IN devolveria quem tem qualquer uma das duas; a spec pede quem tem as duas.
        assertThat(montada.sql()).doesNotContain("i.slug in");
        assertThat(montada.sql().split("exists \\(", -1)).hasSize(3);
        assertThat(montada.parametros().getValue("integracao0")).isEqualTo("whatsapp");
        assertThat(montada.parametros().getValue("integracao1")).isEqualTo("hubspot");
    }

    @Test
    @DisplayName("modelos de cobranca viram um IN: basta um deles")
    void modelosSaoCombinadosComOu() {
        var montada = builder.montar(consulta(Set.of(), List.of(), List.of("FREE", "FREEMIUM"), null, null, null));

        assertThat(montada.sql()).contains("p.pricing_model in (:modelos)");
        assertThat((List<String>) montada.parametros().getValue("modelos")).containsExactly("FREE", "FREEMIUM");
    }

    @Test
    @DisplayName("price_max exclui explicitamente quem nao tem preco divulgado")
    void precoMaximoExcluiSemPreco() {
        String sql = builder.montar(consulta(Set.of(), List.of(), List.of(), null, null, new BigDecimal("100")))
                .sql();

        assertThat(sql).contains("p.price_min is not null").contains("p.price_min <= :precoMaximo");
    }

    @Test
    @DisplayName("booleanos so entram quando informados")
    void booleanosOpcionais() {
        assertThat(builder.montar(consulta(Set.of(), List.of(), List.of(), null, null, null))
                        .sql())
                .doesNotContain("has_free_trial =");

        assertThat(builder.montar(consulta(Set.of(), List.of(), List.of(), false, null, null))
                        .sql())
                .contains("p.has_free_trial = :temTeste");
    }

    @Test
    @DisplayName("com texto, entra o lateral da consulta e o ranking troca a ordenacao")
    void textoEntraNoRanking() {
        var comTexto = new ProductSearchQuery(
                AppLocale.PT_BR,
                new PagingParams(0, 20),
                "atendimento",
                Set.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        var montada = builder.montar(comTexto);

        assertThat(montada.sql())
                .contains("websearch_to_tsquery('portuguese', f_unaccent(:texto))")
                .contains("p.search_vector_pt @@ busca.tsq")
                .contains("order by ts_rank_cd(p.search_vector_pt, busca.tsq, 32) desc");
        assertThat(montada.parametros().getValue("texto")).isEqualTo("atendimento");
    }

    @Test
    @DisplayName("o dicionario acompanha o idioma pedido")
    void dicionarioAcompanhaIdioma() {
        var emIngles = new ProductSearchQuery(
                AppLocale.EN, new PagingParams(0, 20), "support", Set.of(), List.of(), List.of(), null, null, null);

        assertThat(builder.montar(emIngles).sql())
                .contains("websearch_to_tsquery('english'")
                .contains("p.search_vector_en @@ busca.tsq");
    }

    @Test
    @DisplayName("sem texto, nada de lateral nem de ranking")
    void semTextoNaoTemRanking() {
        String sql = builder.montar(consulta(Set.of(), List.of(), List.of(), null, null, null))
                .sql();

        assertThat(sql)
                .doesNotContain("websearch_to_tsquery")
                .doesNotContain("ts_rank_cd")
                .contains("order by p.updated_at desc");
    }

    @Test
    @DisplayName("o idioma escolhe as colunas de texto")
    void idiomaEscolheColunas() {
        var emIngles = new ProductSearchQuery(
                AppLocale.EN, new PagingParams(0, 20), null, Set.of(), List.of(), List.of(), null, null, null);

        assertThat(builder.montar(emIngles).sql()).contains("p.tagline_en").doesNotContain("p.tagline_pt");
    }

    @Test
    @DisplayName("o fallback compara por similaridade de nome, com o limiar da spec")
    void fallbackUsaSimilaridadeDeNome() {
        var comTexto = new ProductSearchQuery(
                AppLocale.PT_BR, new PagingParams(0, 20), "chatvold", Set.of(), List.of(), List.of(), null, null, null);

        var montada = builder.montarPorSimilaridade(comTexto);

        assertThat(montada.sql())
                .contains("similarity(lower(f_unaccent(p.name)), lower(f_unaccent(:texto)))")
                .contains("semelhanca.sim > 0.3")
                .contains("order by semelhanca.sim desc");
        assertThat(montada.parametros().getValue("texto")).isEqualTo("chatvold");
    }

    @Test
    @DisplayName("o fallback nao mistura full-text: nada de tsquery nem de ts_rank_cd")
    void fallbackNaoUsaFullText() {
        var comTexto = new ProductSearchQuery(
                AppLocale.PT_BR, new PagingParams(0, 20), "chatvold", Set.of(), List.of(), List.of(), null, null, null);

        assertThat(builder.montarPorSimilaridade(comTexto).sql())
                .doesNotContain("websearch_to_tsquery")
                .doesNotContain("ts_rank_cd")
                .doesNotContain("search_vector");
    }

    @Test
    @DisplayName("o fallback carrega os mesmos filtros da busca principal")
    void fallbackMantemOsFiltros() {
        UUID categoria = UUID.randomUUID();
        var comTexto = new ProductSearchQuery(
                AppLocale.EN,
                new PagingParams(0, 20),
                "chatvold",
                Set.of(categoria),
                List.of("whatsapp"),
                List.of("FREE"),
                true,
                null,
                new BigDecimal("100"));

        var montada = builder.montarPorSimilaridade(comTexto);

        assertThat(montada.sql())
                .contains("p.status = 'PUBLISHED'")
                .contains("p.category_id in (:categorias)")
                .contains("exists (")
                .contains("p.pricing_model in (:modelos)")
                .contains("p.has_free_trial = :temTeste")
                .contains("p.price_min <= :precoMaximo")
                // e o idioma continua escolhendo as colunas de texto
                .contains("p.tagline_en");
        assertThat(montada.parametros().getValue("integracao0")).isEqualTo("whatsapp");
    }

    @Test
    @DisplayName("a contagem nao pagina nem ordena, mas usa os mesmos filtros da pagina")
    void contagemUsaOsMesmosFiltros() {
        var comFiltros = consulta(Set.of(), List.of("whatsapp"), List.of("FREEMIUM"), true, null, new BigDecimal("99"));

        var contagem = builder.contar(comFiltros);
        var pagina = builder.montar(comFiltros);

        assertThat(contagem.sql())
                .contains("select count(*)")
                .contains("p.status = 'PUBLISHED'")
                .contains("i.slug = :integracao0")
                .contains("p.pricing_model in (:modelos)")
                .contains("p.has_free_trial = :temTeste")
                .contains("p.price_min <= :precoMaximo")
                // Sem limit/offset e o ponto: e o total, e nao o total da pagina.
                .doesNotContain("limit")
                .doesNotContain("order by");
        // Contagem que nao usa os mesmos predicados da pagina e uma segunda fonte
        // da verdade; as duas divergiriam na primeira mudanca de filtro.
        assertThat(pagina.sql()).contains("i.slug = :integracao0").contains("p.pricing_model in (:modelos)");
    }

    @Test
    @DisplayName("a contagem do full-text carrega a tsquery, e a do fallback a similaridade")
    void contagemAcompanhaOCaminho() {
        var comTexto = new ProductSearchQuery(
                AppLocale.PT_BR, new PagingParams(0, 20), "chatvold", Set.of(), List.of(), List.of(), null, null, null);

        assertThat(builder.contar(comTexto).sql())
                .contains("websearch_to_tsquery")
                .contains("p.search_vector_pt @@ busca.tsq");
        assertThat(builder.contarPorSimilaridade(comTexto).sql())
                .contains("semelhanca.sim > 0.3")
                .doesNotContain("websearch_to_tsquery");
    }

    @Test
    @DisplayName("a pagina nao carrega mais o total: ele nao pode depender de haver linha")
    void paginaNaoCarregaTotal() {
        // `count(*) over ()` sumia junto com as linhas quando o offset passava do
        // fim, e o cliente recebia total 0 num filtro que casa com dezenas (T28).
        assertThat(builder.montar(consulta(Set.of(), List.of(), List.of(), null, null, null))
                        .sql())
                .doesNotContain("count(*) over ()");
    }
}
