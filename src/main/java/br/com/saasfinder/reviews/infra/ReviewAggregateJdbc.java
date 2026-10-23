package br.com.saasfinder.reviews.infra;

import br.com.saasfinder.reviews.domain.NotaDoProduto;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * O agregado de nota, numa consulta so para quantos produtos forem (plano DT-06).
 *
 * <p>JDBC direto, e nao JPA: e uma projecao de agregacao, nao um objeto de
 * dominio - mapear isto como entidade seria dar identidade a algo que e o
 * resultado de um {@code group by}.
 *
 * <p>Uma consulta <b>por pagina</b>, alimentada pelos ids que a lista ja tem. E o
 * que impede o N+1 do AC-14: com uma consulta por produto, uma listagem de 20
 * produtos faria 21 idas ao banco.
 */
@Repository
public class ReviewAggregateJdbc {

    private static final String SQL =
            """
            select product_id,
                   round(avg(rating)::numeric, 1)                                    as media,
                   count(*)                                                          as total,
                   round(count(*) filter (where would_recommend)::numeric
                         / count(*), 2)                                              as recomenda,
                   count(*) filter (where verified)                                  as verificadas
              from review
             where status = 'PUBLISHED'
               and product_id in (:produtos)
             group by product_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewAggregateJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * As notas destes produtos, chaveadas por id.
     *
     * <p>Produto sem review publicada simplesmente <b>nao aparece</b> no mapa: quem
     * pergunta traduz a ausencia em {@code rating: null} (spec secao 4.4). Devolver
     * um agregado zerado aqui empurraria a decisao para cada chamador, e um deles
     * escreveria zero na tela.
     */
    public Map<UUID, NotaDoProduto> notasDe(Collection<UUID> produtos) {
        if (produtos.isEmpty()) {
            return Map.of();
        }

        Map<UUID, NotaDoProduto> notas = new HashMap<>();
        jdbc.query(SQL, new MapSqlParameterSource("produtos", produtos), linha -> {
            notas.put(
                    linha.getObject("product_id", UUID.class),
                    new NotaDoProduto(
                            linha.getBigDecimal("media"),
                            linha.getLong("total"),
                            linha.getBigDecimal("recomenda"),
                            linha.getLong("verificadas")));
        });
        return notas;
    }
}
