package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.shared.error.ApiException;
import br.com.saasfinder.shared.error.ProblemTypes;

/**
 * Token de confirmacao usado, expirado ou desconhecido (AC-03, AC-04).
 *
 * <p><b>Os tres casos respondem igual</b>, de proposito: distinguir "ja usado" de
 * "nao existe" diria a quem chuta tokens quais deles um dia existiram. Quem
 * confirmou de verdade nao precisa da distincao - a tela oferece o reenvio nos
 * tres casos.
 */
public class LinkNaoValeMaisException extends ApiException {

    public LinkNaoValeMaisException() {
        super(ProblemTypes.GONE, "problema.gone.detalhe");
    }
}
