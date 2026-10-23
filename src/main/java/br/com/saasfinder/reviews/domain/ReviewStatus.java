package br.com.saasfinder.reviews.domain;

/**
 * Ciclo de vida de uma review (spec secao 4.1, plano DT-03).
 *
 * <p>E uma maquina de estados, e nao um par de booleanos: dois booleanos
 * ({@code confirmado}, {@code publicado}) permitiriam quatro combinacoes, e uma
 * delas - publicada sem confirmar - nao deveria existir.
 */
public enum ReviewStatus {

    /** Escrita, e-mail ainda nao confirmado. Nao aparece em lugar nenhum. */
    PENDING_EMAIL,

    /** E-mail confirmado. Esta na fila do curador. */
    PENDING_MODERATION,

    /** Aprovada pelo curador. E o unico estado visivel ao publico. */
    PUBLISHED,

    /** Recusada pelo curador, sempre com motivo. Nao volta para pendente. */
    REJECTED
}
