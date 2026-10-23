package br.com.saasfinder.catalog.api.dto;

import br.com.saasfinder.search.domain.ProductSummaryView;
import java.util.List;

/**
 * Envelope de {@code GET /products} (spec secao 5.1).
 *
 * <p>Traz `locale` no corpo, e nao so no header Content-Language, para ficar
 * igual ao envelope de /categories - rotas irmas com formatos diferentes seriam
 * ruido para quem consome.
 */
public record ProductListResponse(
        String locale, List<ProductSummaryView> items, int page, int size, long total, ProductQueryEcho query) {}
