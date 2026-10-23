package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.shared.locale.AppLocale;
import java.util.UUID;

/**
 * O curador aprovou, e quem escreveu merece saber (spec Q-02).
 *
 * <p>Avisa quando publica, e <b>nao</b> quando rejeita: silencio depois de "em
 * analise" e ruim, mas e-mail de rejeicao abre uma discussao por e-mail que
 * ninguem vai moderar - e a politica publica ja diz o que e recusado.
 */
public record ReviewPublicada(
        UUID reviewId,
        String destinatario,
        AppLocale idioma,
        String nomeDoAutor,
        String nomeDoProduto,
        String slugDoProduto) {}
