package br.com.saasfinder.reviews.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.reviews.domain.Review;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/**
 * Criterio de verificacao da tarefa 003-T02: a regra do selo (spec secao 4.5).
 *
 * <p>Os casos passam pelo {@code Review.dominioDe} de proposito, e nao por um
 * dominio digitado a mao: e esse o caminho de producao - o e-mail vira dominio na
 * criacao da review, e o dominio e o que a lista consulta.
 */
class ProvedoresGratuitosTest {

    private static final ProvedoresGratuitos LISTA =
            new ProvedoresGratuitos(new ClassPathResource("reviews/provedores-gratuitos.txt"));

    private static boolean gratuitoPara(String email) {
        return LISTA.ehGratuito(Review.dominioDe(email));
    }

    @Test
    @DisplayName("dominio proprio nao e provedor gratuito: a review ganha o selo")
    void dominioProprioEVerificado() {
        assertThat(gratuitoPara("ana@empresa.com.br")).isFalse();
    }

    @Test
    @DisplayName("gmail.com e provedor gratuito: a review entra sem selo")
    void gmailNaoEVerificado() {
        assertThat(gratuitoPara("ana@gmail.com")).isTrue();
    }

    @Test
    @DisplayName("a comparacao e em minusculas: GMAIL.COM tambem fica sem selo")
    void comparacaoIgnoraCaixa() {
        assertThat(gratuitoPara("Ana@GMAIL.COM")).isTrue();
        assertThat(LISTA.ehGratuito("GMail.Com")).isTrue();
    }

    @Test
    @DisplayName("dominio desconhecido e tratado como proprio, e ganha o selo")
    void desconhecidoEVerificado() {
        assertThat(gratuitoPara("ana@cooperativa-de-laticinios-do-vale.coop.br"))
                .isFalse();
    }

    @Test
    @DisplayName("a lista publicada tem os provedores que a spec nomeia")
    void aListaPublicadaEstaCarregada() {
        ProvedoresGratuitos doClasspath = new ProvedoresGratuitos();

        assertThat(doClasspath.quantidade()).isGreaterThan(20);
        assertThat(doClasspath.ehGratuito("gmail.com")).isTrue();
        assertThat(doClasspath.ehGratuito("outlook.com")).isTrue();
        assertThat(doClasspath.ehGratuito("hotmail.com")).isTrue();
        assertThat(doClasspath.ehGratuito("yahoo.com")).isTrue();
    }

    @Test
    @DisplayName("comentario e linha vazia nao viram dominio")
    void comentariosSaoIgnorados() {
        ProvedoresGratuitos lista = de(
                """
                # comentario de fonte
                gmail.com

                   outlook.com
                """);

        assertThat(lista.quantidade()).isEqualTo(2);
        assertThat(lista.ehGratuito("outlook.com")).isTrue();
        assertThat(lista.ehGratuito("#")).isFalse();
    }

    @Test
    @DisplayName("arquivo vazio derruba o boot, em vez de marcar todo mundo como verificado")
    void arquivoVazioFalha() {
        assertThatThrownBy(() -> de("# so comentario, nenhum dominio\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    @DisplayName("arquivo ausente derruba o boot")
    void arquivoAusenteFalha() {
        assertThatThrownBy(() -> new ProvedoresGratuitos(new ClassPathResource("reviews/nao-existe.txt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao encontrada");
    }

    private static ProvedoresGratuitos de(String conteudo) {
        return new ProvedoresGratuitos(new ByteArrayResource(conteudo.getBytes(StandardCharsets.UTF_8)));
    }
}
