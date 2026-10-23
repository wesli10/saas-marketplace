package br.com.saasfinder.reviews.infra.mail;

import br.com.saasfinder.reviews.domain.ConfirmacaoSolicitada;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.domain.ReviewPublicada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda o e-mail <b>depois</b> que a review esta gravada (plano DT-05).
 *
 * <p>Dentro da transacao, uma falha de SMTP desfaria uma review perfeitamente boa;
 * o AC-07 exige o contrario. Com {@code AFTER_COMMIT}, quando este codigo roda a
 * review ja existe e nada do que acontecer aqui a apaga.
 *
 * <p>Por isso tambem o {@code catch}: excecao lancada num listener de
 * {@code AFTER_COMMIT} sobe para quem chamou o commit, e transformaria um 202 em
 * 500 <i>depois</i> de a review ter sido gravada - a pior das combinacoes, porque
 * quem escreveu acharia que perdeu o texto. O caminho de recuperacao e o reenvio.
 */
@Component
public class NotificadorDeReview {

    private static final Logger log = LoggerFactory.getLogger(NotificadorDeReview.class);

    private final Mensagens mensagens;
    private final EnviadorDeEmail enviador;

    public NotificadorDeReview(Mensagens mensagens, EnviadorDeEmail enviador) {
        this.mensagens = mensagens;
        this.enviador = enviador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoPedirConfirmacao(ConfirmacaoSolicitada evento) {
        enviarSemDerrubar(
                evento.reviewId(),
                () -> mensagens.confirmacao(
                        evento.destinatario(),
                        evento.idioma(),
                        evento.nomeDoAutor(),
                        evento.nomeDoProduto(),
                        evento.slugDoProduto(),
                        evento.tokenEmClaro()));
    }

    /**
     * Avisa quem escreveu que a review saiu (spec Q-02).
     *
     * <p>Depois do commit pelo mesmo motivo do outro: uma falha de SMTP nao pode
     * desfazer uma publicacao que o curador ja decidiu.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoPublicar(ReviewPublicada evento) {
        enviarSemDerrubar(
                evento.reviewId(),
                () -> mensagens.publicada(
                        evento.destinatario(),
                        evento.idioma(),
                        evento.nomeDoAutor(),
                        evento.nomeDoProduto(),
                        evento.slugDoProduto()));
    }

    private void enviarSemDerrubar(
            java.util.UUID reviewId, java.util.function.Supplier<br.com.saasfinder.reviews.domain.Email> mensagem) {
        try {
            enviador.enviar(mensagem.get());
        } catch (RuntimeException falha) {
            // Sem o e-mail e sem o token no log (constituicao secao 6): o id da
            // review basta para achar o registro e reenviar.
            log.warn("nao foi possivel enviar o e-mail da review {}", reviewId, falha);
        }
    }
}
