package br.com.saasfinder.reviews.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.saasfinder.reviews.domain.Email;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Criterio de verificacao da tarefa 003-T03: o adapter monta a mensagem certa,
 * engole a falha do servidor (AC-07) e nao escreve e-mail nem token no log
 * (constituicao secao 6).
 *
 * <p>O {@link JavaMailSender} e dublado, e nao um servidor de verdade: quem prova
 * que a entrega funciona de ponta a ponta e o Mailpit, no compose e na T10.
 */
class SmtpEnviadorDeEmailTest {

    private final JavaMailSender enviador = mock(JavaMailSender.class);
    private final SmtpEnviadorDeEmail smtp = new SmtpEnviadorDeEmail(enviador, "reviews@exemplo.test");

    private ListAppender<ILoggingEvent> linhasDeLog;
    private Logger logger;

    @BeforeEach
    void capturarLog() {
        linhasDeLog = new ListAppender<>();
        linhasDeLog.start();
        logger = (Logger) LoggerFactory.getLogger(SmtpEnviadorDeEmail.class);
        logger.addAppender(linhasDeLog);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(linhasDeLog);
    }

    @Test
    @DisplayName("monta remetente, destinatario, assunto e corpo")
    void montaAMensagem() {
        smtp.enviar(new Email("ana@acme.com.br", "Confirme", "corpo com https://exemplo.test?token=segredo"));

        ArgumentCaptor<SimpleMailMessage> capturada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(enviador).send(capturada.capture());

        SimpleMailMessage mensagem = capturada.getValue();
        assertThat(mensagem.getFrom()).isEqualTo("reviews@exemplo.test");
        assertThat(mensagem.getTo()).containsExactly("ana@acme.com.br");
        assertThat(mensagem.getSubject()).isEqualTo("Confirme");
        assertThat(mensagem.getText()).contains("token=segredo");
    }

    @Test
    @DisplayName("falha do servidor nao sobe: a review ja gravada nao pode ser perdida (AC-07)")
    void falhaNaoPropaga() {
        doThrow(new MailSendException("conexao recusada")).when(enviador).send(any(SimpleMailMessage.class));

        assertThatCode(() -> smtp.enviar(new Email("ana@acme.com.br", "Confirme", "corpo")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o log registra o dominio, e nunca o e-mail completo nem o token")
    void logNaoVazaDadoPessoal() {
        smtp.enviar(new Email("ana.ribeiro@acme.com.br", "Confirme", "link com token=segredo-do-token"));
        doThrow(new MailSendException("conexao recusada")).when(enviador).send(any(SimpleMailMessage.class));
        smtp.enviar(new Email("ana.ribeiro@acme.com.br", "Confirme", "link com token=segredo-do-token"));

        assertThat(linhasDeLog.list).hasSize(2);
        for (ILoggingEvent linha : linhasDeLog.list) {
            String texto = linha.getFormattedMessage();
            assertThat(texto).contains("acme.com.br");
            assertThat(texto).doesNotContain("ana.ribeiro");
            assertThat(texto).doesNotContain("segredo-do-token");
        }
    }
}
