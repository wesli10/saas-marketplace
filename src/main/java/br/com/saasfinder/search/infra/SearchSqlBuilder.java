package br.com.saasfinder.search.infra;

import br.com.saasfinder.search.domain.ProductSearchQuery;
import br.com.saasfinder.shared.locale.AppLocale;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Monta o SQL da busca a partir dos filtros pedidos.
 *
 * <p>Fica separado do repositorio para poder ser testado sem banco: a combinacao
 * de filtros e onde mora o risco, nao a execucao.
 *
 * <p>Sao dois caminhos de busca sobre os mesmos filtros: o full-text ({@link
 * #montar}) e o resgate por similaridade ({@link #montarPorSimilaridade}), usado
 * so quando o primeiro nao devolve nada.
 *
 * <p>Cada caminho rende duas consultas: a pagina e a contagem. Sao separadas
 * porque o total nao pode depender de haver linha - com o offset alem do fim,
 * `count(*) over ()` some junto com as linhas e o cliente recebe `total: 0` num
 * filtro que casa com dezenas (defeito encontrado no 002-T19, AC-05).
 */
public class SearchSqlBuilder {

    /**
     * Limiar do fallback por similaridade (spec secao 5.1).
     *
     * <p>Vai literal no SQL, e nao como parametro, porque e regra da spec e nao
     * entrada do usuario - deixa o plano de execucao estavel e o teste legivel.
     */
    static final String LIMIAR_TRIGRAM = "0.3";

    private static final String ORDENACAO_PADRAO = "p.updated_at desc, p.id desc";

    /** SQL e parametros prontos para o NamedParameterJdbcTemplate. */
    public record Consulta(String sql, MapSqlParameterSource parametros) {}

    /** Recorte comum aos dois caminhos: filtros que nao dependem do texto. */
    private record Filtros(List<String> condicoes, MapSqlParameterSource parametros) {}

    /** Um caminho de busca ja resolvido: os filtros, a juncao e a ordenacao dele. */
    private record Caminho(Filtros filtros, String juncaoDeBusca, String ordenacao) {}

    public Consulta montar(ProductSearchQuery consulta) {
        return montarConsulta(consulta, caminhoFullText(consulta));
    }

    /**
     * Quantos casam no total, independente da pagina pedida.
     *
     * <p>E ela que decide se houve casamento - e nao a pagina ter vindo vazia.
     * Trocar as duas coisas fazia uma busca legitima na pagina 90 ser relatada
     * como {@code NONE}.
     */
    public Consulta contar(ProductSearchQuery consulta) {
        return montarContagem(caminhoFullText(consulta));
    }

    public Consulta contarPorSimilaridade(ProductSearchQuery consulta) {
        return montarContagem(caminhoSimilaridade(consulta));
    }

    private Caminho caminhoFullText(ProductSearchQuery consulta) {
        Filtros filtros = filtrosComuns(consulta);

        // A consulta textual e calculada uma vez, num lateral, e reaproveitada no
        // filtro e no ranking. Repetir a expressao nos dois lugares funcionaria, mas
        // deixaria o SQL com duas fontes da verdade para a mesma coisa.
        //
        // websearch_to_tsquery e nao to_tsquery: aceita aspas, `or` e `-` como o
        // usuario escreve, e nunca estoura com entrada livre (D-05).
        String juncaoDeBusca = "";
        String ordenacao = ORDENACAO_PADRAO;
        if (consulta.temTexto()) {
            String sufixo = sufixo(consulta);
            juncaoDeBusca =
                    "  cross join lateral (select websearch_to_tsquery('%s', f_unaccent(:texto)) as tsq) busca\n"
                            .formatted(consulta.locale().dicionarioFts());
            filtros.condicoes().add("p.search_vector_%s @@ busca.tsq".formatted(sufixo));
            // O flag 32 normaliza o rank para rank/(rank+1), o que mantem o valor
            // comparavel entre linhas do mesmo resultado.
            ordenacao = "ts_rank_cd(p.search_vector_%s, busca.tsq, 32) desc, p.updated_at desc, p.id desc"
                    .formatted(sufixo);
            filtros.parametros().addValue("texto", consulta.q().trim());
        }

        return new Caminho(filtros, juncaoDeBusca, ordenacao);
    }

    /**
     * Segunda tentativa, so quando o full-text devolveu zero linhas (AC-12).
     *
     * <p>Cobre apenas o `name` (spec secao 4.6): erro de digitacao em tagline ou
     * descricao nao e tolerado, e isso e escolha, nao esquecimento.
     *
     * <p>Usa `similarity(...) > 0.3` explicito em vez do operador `%`, que depende
     * do GUC `pg_trgm.similarity_threshold` e faria o resultado variar conforme a
     * sessao. O preco e que o indice trigram do `name` nao e usado neste predicado
     * - aceitavel porque o caminho so roda quando a busca principal ja falhou.
     */
    public Consulta montarPorSimilaridade(ProductSearchQuery consulta) {
        return montarConsulta(consulta, caminhoSimilaridade(consulta));
    }

    private Caminho caminhoSimilaridade(ProductSearchQuery consulta) {
        Filtros filtros = filtrosComuns(consulta);

        // Mesma normalizacao do indice criado na V5: minusculas e sem acento nos dois
        // lados da comparacao.
        String juncaoDeBusca =
                "  cross join lateral (select similarity(lower(f_unaccent(p.name)), lower(f_unaccent(:texto))) as sim) semelhanca\n";
        filtros.condicoes().add("semelhanca.sim > " + LIMIAR_TRIGRAM);
        filtros.parametros().addValue("texto", consulta.q().trim());

        return new Caminho(filtros, juncaoDeBusca, "semelhanca.sim desc, " + ORDENACAO_PADRAO);
    }

    private String sufixo(ProductSearchQuery consulta) {
        return consulta.locale() == AppLocale.PT_BR ? "pt" : "en";
    }

    private Filtros filtrosComuns(ProductSearchQuery consulta) {
        List<String> condicoes = new ArrayList<>();
        MapSqlParameterSource parametros = new MapSqlParameterSource();

        condicoes.add("p.status = 'PUBLISHED'");

        if (!consulta.categorias().isEmpty()) {
            condicoes.add("p.category_id in (:categorias)");
            parametros.addValue("categorias", consulta.categorias());
        }

        // E entre integracoes: um EXISTS por slug pedido. Um IN devolveria quem tem
        // qualquer uma delas, que e o oposto do que a spec pede.
        int indice = 0;
        for (String slug : consulta.integrationSlugs()) {
            String parametro = "integracao" + indice++;
            condicoes.add(
                    """
                    exists (select 1 from product_integration pi
                              join integration i on i.id = pi.integration_id
                             where pi.product_id = p.id and i.slug = :%s)
                    """
                            .formatted(parametro));
            parametros.addValue(parametro, slug);
        }

        // OU entre modelos de cobranca.
        if (!consulta.pricingModels().isEmpty()) {
            condicoes.add("p.pricing_model in (:modelos)");
            parametros.addValue("modelos", consulta.pricingModels());
        }

        if (consulta.hasFreeTrial() != null) {
            condicoes.add("p.has_free_trial = :temTeste");
            parametros.addValue("temTeste", consulta.hasFreeTrial());
        }

        if (consulta.hasFreePlan() != null) {
            condicoes.add("p.has_free_plan = :temPlanoGratis");
            parametros.addValue("temPlanoGratis", consulta.hasFreePlan());
        }

        // Produto sem preco divulgado fica de fora do filtro por preco (AC-14): nao da
        // para afirmar que cabe no teto se o preco nao e publico.
        if (consulta.priceMax() != null) {
            condicoes.add("p.price_min is not null and p.price_min <= :precoMaximo");
            parametros.addValue("precoMaximo", consulta.priceMax());
        }

        return new Filtros(condicoes, parametros);
    }

    private Consulta montarConsulta(ProductSearchQuery consulta, Caminho caminho) {
        Filtros filtros = caminho.filtros();
        String sql =
                """
                select p.id,
                       p.slug,
                       p.name,
                       p.tagline_%1$s          as tagline,
                       p.logo_url,
                       c.slug_%1$s             as categoria_slug,
                       c.name_%1$s             as categoria_nome,
                       p.pricing_model,
                       p.price_min,
                       p.price_currency,
                       p.price_period,
                       p.has_free_trial,
                       p.has_free_plan
                  from product p
                  join category c on c.id = p.category_id
                %3$s where %2$s
                 order by %4$s
                 limit :size offset :offset
                """
                        .formatted(
                                sufixo(consulta),
                                String.join("\n   and ", filtros.condicoes()),
                                caminho.juncaoDeBusca(),
                                caminho.ordenacao());

        MapSqlParameterSource parametros = filtros.parametros();
        parametros.addValue("size", consulta.paginacao().size());
        parametros.addValue("offset", consulta.paginacao().offset());

        return new Consulta(sql, parametros);
    }

    /**
     * A mesma consulta sem projecao, sem ordem e sem recorte de pagina.
     *
     * <p>Mesmos filtros e mesma juncao da pagina, de proposito: contagem que nao
     * usa exatamente os mesmos predicados e uma segunda fonte da verdade, e as
     * duas divergem na primeira mudanca de filtro.
     */
    private Consulta montarContagem(Caminho caminho) {
        String sql =
                """
                select count(*)
                  from product p
                  join category c on c.id = p.category_id
                %2$s where %1$s
                """
                        .formatted(String.join("\n   and ", caminho.filtros().condicoes()), caminho.juncaoDeBusca());

        return new Consulta(sql, caminho.filtros().parametros());
    }
}
