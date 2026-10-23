package br.com.saasfinder.reviews.domain;

/**
 * Uma mensagem pronta para sair (plano DT-05).
 *
 * <p>Texto puro, sem HTML: sao duas frases e um link. O corpo ja vem no idioma em
 * que a review foi escrita (AC-16) - quem escolhe o idioma e quem monta a
 * mensagem, nao quem envia.
 */
public record Email(String destinatario, String assunto, String corpo) {}
