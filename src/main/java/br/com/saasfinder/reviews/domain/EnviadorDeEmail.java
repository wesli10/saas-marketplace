package br.com.saasfinder.reviews.domain;

/**
 * Porta de saida para e-mail (plano DT-05).
 *
 * <p>Interface no dominio, sem nada de Spring: quem envia e detalhe de infra, e o
 * dominio nao muda quando o SMTP muda (risco R-03). Em desenvolvimento o outro
 * lado e o Mailpit; em producao, o SMTP de verdade; no teste, um dublê.
 */
public interface EnviadorDeEmail {

    /**
     * Envia, ou <b>registra a falha sem propaga-la</b>.
     *
     * <p>E-mail e o unico ponto desta fatia que depende de terceiro, e ele nao pode
     * derrubar o que ja deu certo: a review fica em {@code PENDING_EMAIL} e o
     * reenvio funciona (AC-07).
     */
    void enviar(Email email);
}
