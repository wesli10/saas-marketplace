package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.reviews.domain.ReviewModeracaoView;
import java.util.List;

/** Envelope da fila de moderacao: a mesma forma dos outros envelopes da API. */
public record AdminReviewListResponse(List<ReviewModeracaoView> items, int page, int size, long total) {}
