package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.ProductAdminView;
import java.util.List;

/**
 * Envelope da listagem de curadoria (plan.md DT-12).
 *
 * <p>Mesma forma do envelope publico - items, page, size, total -, sem `locale`:
 * a resposta traz os dois idiomas, entao nao ha idioma resolvido a anunciar.
 */
public record AdminProductListResponse(List<ProductAdminView> items, int page, int size, long total) {}
