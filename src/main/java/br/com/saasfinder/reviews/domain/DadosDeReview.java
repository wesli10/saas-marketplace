package br.com.saasfinder.reviews.domain;

/**
 * O que quem escreve informa (spec secao 3). Ja validado na borda: o dominio
 * recebe dado valido (constituicao secao 6).
 */
public record DadosDeReview(
        int rating, boolean wouldRecommend, String title, String body, String authorName, String authorEmail) {}
