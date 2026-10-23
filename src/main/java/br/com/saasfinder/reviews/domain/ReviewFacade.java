package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.reviews.infra.ReviewAggregateJdbc;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada do modulo {@code reviews} para os outros modulos (plano DT-01).
 *
 * <p>O {@code catalog} precisa da nota para o card e para a ficha, e chega nela
 * por aqui - nunca pelo {@code ReviewRepository}, como manda a constituicao
 * secao 4.
 *
 * <p>A assinatura recebe uma <b>colecao</b> de propósito. Um
 * {@code notaDe(UUID)} seria mais confortavel de chamar e convidaria ao laco que
 * o AC-14 proibe: com 20 produtos por pagina, seriam 20 consultas onde uma basta.
 * A forma da API e o que impede o defeito, e nao a disciplina de quem chama.
 */
@Service
public class ReviewFacade {

    private final ReviewAggregateJdbc agregados;

    public ReviewFacade(ReviewAggregateJdbc agregados) {
        this.agregados = agregados;
    }

    /**
     * As notas destes produtos, numa consulta so.
     *
     * <p>Produto sem review publicada <b>nao aparece no mapa</b>: quem monta a view
     * traduz a ausencia em {@code rating: null} (spec secao 4.4).
     */
    @Transactional(readOnly = true)
    public Map<UUID, NotaDoProduto> notasDe(Collection<UUID> produtos) {
        return agregados.notasDe(produtos);
    }
}
