package br.com.saasfinder.catalog.api.dto;

import br.com.saasfinder.catalog.domain.ProductDetailView;
import java.util.List;

/**
 * Envelope de {@code GET /products/compare} (plan.md secao 4.5).
 *
 * <p>Mesma ficha da rota individual, em array e na ordem pedida: comparar e ler
 * as mesmas linhas lado a lado, entao um formato so para os dois casos.
 */
public record ProductCompareResponse(String locale, List<ProductDetailView> items) {}
