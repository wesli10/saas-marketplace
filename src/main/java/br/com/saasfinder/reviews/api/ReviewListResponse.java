package br.com.saasfinder.reviews.api;

import br.com.saasfinder.reviews.domain.NotaDoProduto;
import java.util.List;

/**
 * Envelope da leitura publica de reviews (spec secao 5.1).
 *
 * @param rating o agregado do produto, ou {@code null} quando nao ha review
 *     publicada - nunca um agregado zerado (spec secao 4.4)
 */
public record ReviewListResponse(NotaDoProduto rating, List<ReviewView> items, int page, int size, long total) {}
