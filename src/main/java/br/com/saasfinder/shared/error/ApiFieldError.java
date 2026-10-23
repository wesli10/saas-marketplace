package br.com.saasfinder.shared.error;

/**
 * Item de {@code errors[]} na resposta de erro.
 *
 * @param field campo ou pendencia que causou a recusa
 * @param code codigo estavel, para o cliente tratar sem depender do texto
 * @param message texto ja no idioma da requisicao
 */
public record ApiFieldError(String field, String code, String message) {}
