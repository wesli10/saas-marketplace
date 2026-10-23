package br.com.saasfinder.search.infra;

import br.com.saasfinder.search.domain.CategoriaResumo;
import br.com.saasfinder.search.domain.IntegracaoResumo;
import br.com.saasfinder.search.domain.MatchedBy;
import br.com.saasfinder.search.domain.ProductSearchQuery;
import br.com.saasfinder.search.domain.ProductSummaryView;
import br.com.saasfinder.search.domain.SearchFacade;
import br.com.saasfinder.search.domain.SearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Motor de consulta do catalogo, em SQL nativo (DT-07).
 *
 * <p>Fica fora do JPA de proposito: filtros combinaveis, ts_rank_cd e o fallback
 * por similaridade nao cabem em JPQL sem virar um gerador de string pior que o
 * proprio SQL. O JPA continua responsavel pela escrita, no modulo catalog.
 */
@Repository
public class ProductSearchJdbcRepository implements SearchFacade {

    private final NamedParameterJdbcTemplate jdbc;
    private final SearchSqlBuilder builder = new SearchSqlBuilder();

    public ProductSearchJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SearchResult buscar(ProductSearchQuery consulta) {
        // A contagem vem primeiro, e e ela que decide tudo: quantos existem, e se
        // houve casamento. Antes quem decidia era a pagina ter vindo com linha, e as
        // duas coisas nao sao a mesma - na pagina 90 de um filtro que casa com 20, a
        // pagina vem vazia e o casamento continua tendo acontecido (AC-05).
        long total = contar(builder.contar(consulta));
        MatchedBy origem = consulta.temTexto() ? MatchedBy.FULL_TEXT : null;
        boolean porSimilaridade = false;

        // Resgate por similaridade (AC-12): so quando havia texto e o full-text nao
        // achou nada. Rodar sempre custaria uma varredura por consulta e ainda poderia
        // misturar aproximacao com casamento exato no mesmo resultado.
        if (total == 0 && consulta.temTexto()) {
            total = contar(builder.contarPorSimilaridade(consulta));
            origem = total == 0 ? MatchedBy.NONE : MatchedBy.TRIGRAM;
            porSimilaridade = total > 0;
        }

        if (total == 0) {
            // Sem texto nao houve casamento a relatar; com texto, nada casou nos dois
            // caminhos.
            return new SearchResult(List.of(), 0, origem);
        }

        List<LinhaDeProduto> linhas =
                executar(porSimilaridade ? builder.montarPorSimilaridade(consulta) : builder.montar(consulta));

        // Pagina alem da ultima: nao ha item para mostrar, mas o total e o caminho do
        // casamento continuam sendo os mesmos. E o que permite ao cliente descobrir
        // que passou do fim, em vez de achar que o filtro nao casa com nada.
        if (linhas.isEmpty()) {
            return new SearchResult(List.of(), total, origem);
        }

        Map<UUID, List<IntegracaoResumo>> integracoes =
                integracoesDe(linhas.stream().map(LinhaDeProduto::id).toList());

        List<ProductSummaryView> itens = linhas.stream()
                .map(linha -> linha.paraView(integracoes.getOrDefault(linha.id(), List.of())))
                .toList();

        return new SearchResult(itens, total, origem);
    }

    private long contar(SearchSqlBuilder.Consulta montada) {
        Long total = jdbc.queryForObject(montada.sql(), montada.parametros(), Long.class);
        return total == null ? 0 : total;
    }

    private List<LinhaDeProduto> executar(SearchSqlBuilder.Consulta montada) {
        return jdbc.query(
                montada.sql(),
                montada.parametros(),
                (rs, indice) -> new LinhaDeProduto(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("tagline"),
                        rs.getString("logo_url"),
                        new CategoriaResumo(rs.getString("categoria_slug"), rs.getString("categoria_nome")),
                        rs.getString("pricing_model"),
                        rs.getBigDecimal("price_min"),
                        rs.getString("price_currency"),
                        rs.getString("price_period"),
                        rs.getBoolean("has_free_trial"),
                        rs.getBoolean("has_free_plan")));
    }

    /**
     * Uma consulta so para todas as integracoes da pagina.
     *
     * <p>Buscar por produto dentro do laco geraria N+1 - com 20 itens por pagina,
     * 21 idas ao banco em vez de 2.
     */
    private Map<UUID, List<IntegracaoResumo>> integracoesDe(List<UUID> produtos) {
        String sql =
                """
                select pi.product_id, i.slug, i.name
                  from product_integration pi
                  join integration i on i.id = pi.integration_id
                 where pi.product_id in (:produtos)
                 order by i.name
                """;

        Map<UUID, List<IntegracaoResumo>> porProduto = new LinkedHashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("produtos", produtos), rs -> {
            UUID produto = rs.getObject("product_id", UUID.class);
            porProduto
                    .computeIfAbsent(produto, chave -> new ArrayList<>())
                    .add(new IntegracaoResumo(rs.getString("slug"), rs.getString("name")));
        });
        return porProduto;
    }

    /** Linha crua, antes de receber as integracoes carregadas em lote. */
    private record LinhaDeProduto(
            UUID id,
            String slug,
            String name,
            String tagline,
            String logoUrl,
            CategoriaResumo categoria,
            String pricingModel,
            java.math.BigDecimal priceMin,
            String priceCurrency,
            String pricePeriod,
            boolean hasFreeTrial,
            boolean hasFreePlan) {

        ProductSummaryView paraView(List<IntegracaoResumo> integracoes) {
            return new ProductSummaryView(
                    id,
                    slug,
                    name,
                    tagline,
                    logoUrl,
                    categoria,
                    pricingModel,
                    priceMin,
                    priceCurrency,
                    pricePeriod,
                    hasFreeTrial,
                    hasFreePlan,
                    integracoes,
                    // A nota vem depois, do modulo reviews: a busca nao conhece review
                    // (constituicao secao 4), e quem a anexa e o catalog.
                    null);
        }
    }
}
