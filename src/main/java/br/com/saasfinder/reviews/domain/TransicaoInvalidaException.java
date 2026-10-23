package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.shared.error.ConflictException;

/**
 * Transicao que a maquina de estados de {@link Review} nao permite (plano DT-03).
 *
 * <p>Estende {@link ConflictException} para sair como <b>409</b>, e nao 400: o
 * pedido esta bem formado; o estado atual e que nao aceita a operacao.
 */
public class TransicaoInvalidaException extends ConflictException {

    public TransicaoInvalidaException(String operacao, ReviewStatus estadoAtual) {
        super("problema.review.transicao-invalida", operacao, estadoAtual.name());
    }
}
