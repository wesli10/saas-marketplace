package br.com.saasfinder.reviews.domain;

import java.util.List;

/** Uma pagina da fila do curador, com o total para quem precisa paginar. */
public record PaginaDeModeracao(List<ReviewModeracaoView> itens, long total) {}
