package br.com.saasfinder.reviews.domain;

import java.util.List;

/**
 * Uma pagina de reviews publicadas, com o agregado do produto.
 *
 * @param nota {@code null} quando nao ha review publicada (spec secao 4.4)
 */
public record PaginaDeReviews(NotaDoProduto nota, List<Review> itens, long total) {}
