package br.com.saasfinder.shared.error;

import java.time.Duration;

/**
 * Limite de taxa estourado (spec 003, secao 8 e Q-01).
 *
 * <p>Carrega quanto falta para liberar, porque isso vira o cabecalho
 * {@code Retry-After} - dizer "espere" sem dizer quanto empurra o cliente a
 * tentar de novo imediatamente.
 */
public class TooManyRequestsException extends ApiException {

    private final transient Duration espera;

    public TooManyRequestsException(Duration espera) {
        super(ProblemTypes.TOO_MANY_REQUESTS, "problema.too-many-requests.detalhe");
        this.espera = espera;
    }

    /** Segundos para o {@code Retry-After}, sempre arredondados para cima e no minimo 1. */
    public long segundosDeEspera() {
        long segundos = (espera.toMillis() + 999) / 1000;
        return Math.max(1, segundos);
    }
}
