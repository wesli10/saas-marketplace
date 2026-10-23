package br.com.saasfinder.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Janela deslizante em memoria, por chave (plano DT-09).
 *
 * <p>Sao dois limites ao mesmo tempo: um curto (por minuto), que barra o script, e
 * um longo (por hora), que barra o script paciente. Guardar os instantes de cada
 * chamada - e nao um contador por balde - e o que torna a janela deslizante de
 * verdade: nao existe virada de minuto que devolva o direito a uma rajada.
 *
 * <p><b>Isto nao sobrevive a duas instancias, nem a um restart</b> (risco R-04).
 * E deliberado: hoje ha uma instancia so, e Bucket4j ou Redis entrariam para
 * resolver um cenario que nao existe. Com duas instancias, o limite dobra na
 * pratica - e ai trocar por um contador compartilhado e uma tarefa, nao uma
 * reescrita.
 */
@Component
public class LimitadorDeTaxa {

    private static final Duration JANELA_CURTA = Duration.ofMinutes(1);
    private static final Duration JANELA_LONGA = Duration.ofHours(1);

    private final int porMinuto;
    private final int porHora;
    private final Clock relogio;

    private final Map<String, Deque<Instant>> historico = new ConcurrentHashMap<>();

    @Autowired
    public LimitadorDeTaxa(
            @Value("${app.rate-limit.por-minuto}") int porMinuto, @Value("${app.rate-limit.por-hora}") int porHora) {
        this(porMinuto, porHora, Clock.systemUTC());
    }

    /** Com relogio proprio: e assim que o teste percorre uma hora sem esperar uma hora. */
    LimitadorDeTaxa(int porMinuto, int porHora, Clock relogio) {
        this.porMinuto = porMinuto;
        this.porHora = porHora;
        this.relogio = relogio;
    }

    /**
     * Registra uma chamada da chave.
     *
     * @return vazio quando passou; com quanto tempo esperar quando estourou - e
     *     esse valor vai no {@code Retry-After}. A chamada barrada <b>nao</b> conta
     *     para a janela: insistir nao empurra a liberacao para a frente, o que
     *     transformaria o limite em punicao crescente por erro de quem tenta.
     */
    public Optional<Duration> registrar(String chave) {
        Instant agora = relogio.instant();
        Deque<Instant> marcas = historico.computeIfAbsent(chave, ignorada -> new ArrayDeque<>());

        synchronized (marcas) {
            marcas.removeIf(marca -> marca.isBefore(agora.minus(JANELA_LONGA)));

            Instant inicioDoMinuto = agora.minus(JANELA_CURTA);
            Optional<Instant> maisAntigaNoMinuto = marcas.stream()
                    .filter(marca -> !marca.isBefore(inicioDoMinuto))
                    .findFirst();

            long noMinuto = marcas.stream()
                    .filter(marca -> !marca.isBefore(inicioDoMinuto))
                    .count();

            if (noMinuto >= porMinuto) {
                return Optional.of(esperaAte(maisAntigaNoMinuto.orElse(agora).plus(JANELA_CURTA), agora));
            }
            if (marcas.size() >= porHora) {
                return Optional.of(esperaAte(marcas.peekFirst().plus(JANELA_LONGA), agora));
            }

            marcas.addLast(agora);
            return Optional.empty();
        }
    }

    /** Nunca devolve zero: um {@code Retry-After: 0} convida a tentar de novo na hora. */
    private static Duration esperaAte(Instant liberacao, Instant agora) {
        Duration falta = Duration.between(agora, liberacao);
        return falta.isNegative() || falta.isZero() ? Duration.ofSeconds(1) : falta;
    }

    /**
     * Esquece as chaves que nao aparecem ha mais de uma hora.
     *
     * <p>Sem isto o mapa cresce com o numero de IPs distintos que ja tentaram, e
     * nunca encolhe - o que e um vazamento de memoria lento, do tipo que so aparece
     * em producao meses depois.
     */
    @Scheduled(fixedDelayString = "${app.rate-limit.limpeza-ms}")
    public void limpar() {
        Instant corte = relogio.instant().minus(JANELA_LONGA);

        historico.forEach((chave, marcas) -> {
            synchronized (marcas) {
                marcas.removeIf(marca -> marca.isBefore(corte));
                if (marcas.isEmpty()) {
                    historico.remove(chave, marcas);
                }
            }
        });
    }
}
