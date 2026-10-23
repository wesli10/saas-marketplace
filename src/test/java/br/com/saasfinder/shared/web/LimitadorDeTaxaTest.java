package br.com.saasfinder.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio de verificacao da tarefa 003-T06: os dois limites da spec Q-01, um por
 * minuto e cinco por hora.
 *
 * <p>O relogio e de mentira, e e o unico jeito honesto de testar isto: provar a
 * sexta chamada da hora esperando de verdade custaria uma hora de suite.
 */
class LimitadorDeTaxaTest {

    private final RelogioDeMentira relogio = new RelogioDeMentira(Instant.parse("2026-08-27T12:00:00Z"));
    private final LimitadorDeTaxa limitador = new LimitadorDeTaxa(1, 5, relogio);

    @Test
    @DisplayName("a primeira passa; a segunda no mesmo minuto e barrada")
    void segundaNoMesmoMinutoEBarrada() {
        assertThat(limitador.registrar("10.0.0.1")).isEmpty();
        assertThat(limitador.registrar("10.0.0.1")).isPresent();
    }

    @Test
    @DisplayName("a espera devolvida e o que falta para a janela do minuto virar")
    void esperaEOQueFaltaDoMinuto() {
        limitador.registrar("10.0.0.1");
        relogio.avancar(Duration.ofSeconds(20));

        assertThat(limitador.registrar("10.0.0.1")).contains(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("passado o minuto, libera de novo")
    void depoisDoMinutoLibera() {
        limitador.registrar("10.0.0.1");
        relogio.avancar(Duration.ofSeconds(61));

        assertThat(limitador.registrar("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("a sexta chamada da hora e barrada, mesmo com um minuto entre cada uma")
    void sextaNaHoraEBarrada() {
        for (int chamada = 1; chamada <= 5; chamada++) {
            assertThat(limitador.registrar("10.0.0.1"))
                    .as("chamada %d da hora", chamada)
                    .isEmpty();
            relogio.avancar(Duration.ofSeconds(61));
        }

        assertThat(limitador.registrar("10.0.0.1")).isPresent();
    }

    @Test
    @DisplayName("a janela e deslizante: a hora vira em relacao a primeira chamada, e nao ao relogio")
    void janelaDeslizanteNaHora() {
        for (int chamada = 1; chamada <= 5; chamada++) {
            limitador.registrar("10.0.0.1");
            relogio.avancar(Duration.ofSeconds(61));
        }
        assertThat(limitador.registrar("10.0.0.1")).isPresent();

        // Uma hora depois da PRIMEIRA chamada, ela sai da janela e sobra uma vaga.
        relogio.avancar(Duration.ofHours(1));
        assertThat(limitador.registrar("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("cada chave tem a propria janela: um IP nao gasta o limite do outro")
    void chavesSaoIndependentes() {
        assertThat(limitador.registrar("10.0.0.1")).isEmpty();
        assertThat(limitador.registrar("10.0.0.2")).isEmpty();
        assertThat(limitador.registrar("10.0.0.1")).isPresent();
        assertThat(limitador.registrar("10.0.0.2")).isPresent();
    }

    @Test
    @DisplayName("insistir nao empurra a liberacao: a chamada barrada nao conta")
    void chamadaBarradaNaoConta() {
        limitador.registrar("10.0.0.1");
        relogio.avancar(Duration.ofSeconds(30));

        limitador.registrar("10.0.0.1");
        limitador.registrar("10.0.0.1");
        relogio.avancar(Duration.ofSeconds(31));

        assertThat(limitador.registrar("10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("a espera nunca e zero: Retry-After: 0 convida a tentar na hora")
    void esperaNuncaEZero() {
        limitador.registrar("10.0.0.1");
        relogio.avancar(Duration.ofSeconds(60).minusMillis(1));

        assertThat(limitador.registrar("10.0.0.1"))
                .hasValueSatisfying(espera -> assertThat(espera).isPositive());
    }

    @Test
    @DisplayName("a limpeza esquece quem nao aparece ha mais de uma hora, e mantem quem apareceu")
    void limpezaEsqueceOsAntigos() {
        limitador.registrar("antigo");
        relogio.avancar(Duration.ofHours(2));
        limitador.registrar("recente");

        limitador.limpar();

        // Depois da limpeza, o antigo comeca do zero (uma vaga livre) e o recente
        // continua com a dele gasta.
        assertThat(limitador.registrar("antigo")).isEmpty();
        assertThat(limitador.registrar("recente")).isPresent();
    }

    /** Relogio que so anda quando o teste manda. */
    private static final class RelogioDeMentira extends Clock {

        private Instant agora;

        private RelogioDeMentira(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }
}
