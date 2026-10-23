package br.com.saasfinder.reviews.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.reviews.domain.Email;
import br.com.saasfinder.shared.locale.AppLocale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio de verificacao da tarefa 003-T03, parte do AC-16: o e-mail sai no
 * idioma em que a review foi escrita, e o link aponta para a rota daquele idioma.
 */
class MensagensTest {

    // Com barra no fim de proposito: a montagem do link nao pode gerar "//".
    private final Mensagens mensagens = new Mensagens("https://exemplo.test/");

    @Test
    @DisplayName("review escrita em pt-BR gera e-mail em portugues, com a rota em portugues")
    void confirmacaoEmPortugues() {
        Email email = mensagens.confirmacao("ana@acme.com.br", AppLocale.PT_BR, "Ana", "Zapier", "zapier", "tok-123");

        assertThat(email.destinatario()).isEqualTo("ana@acme.com.br");
        assertThat(email.assunto()).isEqualTo("Confirme sua avaliacao de Zapier");
        assertThat(email.corpo()).startsWith("Ola, Ana.");
        assertThat(email.corpo()).contains("https://exemplo.test/pt-BR/reviews/confirmar?token=tok-123");
        assertThat(email.corpo()).contains("48 horas");
    }

    @Test
    @DisplayName("review escrita em en gera e-mail em ingles, com a rota em ingles")
    void confirmacaoEmIngles() {
        Email email = mensagens.confirmacao("john@acme.com", AppLocale.EN, "John", "Zapier", "zapier", "tok-123");

        assertThat(email.assunto()).isEqualTo("Confirm your review of Zapier");
        assertThat(email.corpo()).startsWith("Hello, John.");
        assertThat(email.corpo()).contains("https://exemplo.test/en/reviews/confirm?token=tok-123");
        assertThat(email.corpo()).contains("48 hours");
    }

    @Test
    @DisplayName("os dois idiomas trocam o texto inteiro, e nao so o assunto")
    void osDoisTextosSaoDiferentes() {
        Email portugues = mensagens.confirmacao("ana@acme.com.br", AppLocale.PT_BR, "Ana", "Zapier", "zapier", "t");
        Email ingles = mensagens.confirmacao("ana@acme.com.br", AppLocale.EN, "Ana", "Zapier", "zapier", "t");

        assertThat(portugues.corpo()).isNotEqualTo(ingles.corpo());
        assertThat(portugues.assunto()).isNotEqualTo(ingles.assunto());
    }

    @Test
    @DisplayName("nenhum modelo deixa marcador por substituir, e apostrofo nao come texto")
    void modelosNaoDeixamMarcador() {
        for (AppLocale idioma : AppLocale.values()) {
            Email email = mensagens.confirmacao("ana@acme.com.br", idioma, "Ana", "Zapier", "zapier", "tok");

            // {0} sobrando significa argumento faltando; '' sobrando significa que
            // alguem escreveu apostrofo num texto de MessageFormat.
            assertThat(email.assunto()).doesNotContain("{").doesNotContain("''");
            assertThat(email.corpo()).doesNotContain("{").doesNotContain("''");
        }
    }

    @Test
    @DisplayName("o token vai escapado na query")
    void tokenEscapadoNaQuery() {
        String link = mensagens.linkDeConfirmacao(AppLocale.PT_BR, "a+b/c=", "zapier");

        assertThat(link).isEqualTo("https://exemplo.test/pt-BR/reviews/confirmar?token=a%2Bb%2Fc%3D&produto=zapier");
    }
}
