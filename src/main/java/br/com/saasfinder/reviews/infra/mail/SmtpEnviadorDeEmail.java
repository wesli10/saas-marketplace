package br.com.saasfinder.reviews.infra.mail;

import br.com.saasfinder.reviews.domain.Email;
import br.com.saasfinder.reviews.domain.EnviadorDeEmail;
import br.com.saasfinder.reviews.domain.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * O adapter de SMTP (plano DT-05). Em desenvolvimento fala com o Mailpit do
 * compose; em producao, com o SMTP configurado nas variaveis {@code MAIL_*}.
 *
 * <p><b>Falha de envio nao propaga.</b> A review ja esta gravada quando este
 * codigo roda (o envio acontece depois do commit, plano DT-05), e derrubar a
 * requisicao aqui perderia o que ja deu certo - o AC-07 exige o contrario. O que
 * fica e o log; o reenvio e o caminho de recuperacao.
 */
@Component
public class SmtpEnviadorDeEmail implements EnviadorDeEmail {

    private static final Logger log = LoggerFactory.getLogger(SmtpEnviadorDeEmail.class);

    private final JavaMailSender enviador;
    private final String remetente;

    public SmtpEnviadorDeEmail(JavaMailSender enviador, @Value("${app.mail.from}") String remetente) {
        this.enviador = enviador;
        this.remetente = remetente;
    }

    @Override
    public void enviar(Email email) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(email.destinatario());
        mensagem.setSubject(email.assunto());
        mensagem.setText(email.corpo());

        try {
            enviador.send(mensagem);
            // So o dominio, nunca o endereco inteiro (constituicao secao 6). E nunca
            // o corpo, que carrega o token.
            log.info("e-mail enviado para dominio {}", Review.dominioDe(email.destinatario()));
        } catch (MailException falha) {
            // A excecao vai para o log com a mensagem do servidor, que nao contem o
            // destinatario completo nem o corpo.
            log.warn("falha ao enviar e-mail para dominio {}", Review.dominioDe(email.destinatario()), falha);
        }
    }
}
