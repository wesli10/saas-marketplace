package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.Category;
import java.time.Instant;
import java.util.UUID;

/**
 * Categoria como a curadoria a enxerga: os **dois** idiomas de uma vez (spec
 * secao 5), ao contrario da resposta publica, que ja vem resolvida.
 */
public record AdminCategoryResponse(
        UUID id,
        String slugPt,
        String slugEn,
        String namePt,
        String nameEn,
        String descriptionPt,
        String descriptionEn,
        UUID parentId,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminCategoryResponse de(Category categoria) {
        return new AdminCategoryResponse(
                categoria.getId(),
                categoria.getSlugPt(),
                categoria.getSlugEn(),
                categoria.getNamePt(),
                categoria.getNameEn(),
                categoria.getDescriptionPt(),
                categoria.getDescriptionEn(),
                categoria.getParent() == null ? null : categoria.getParent().getId(),
                categoria.getSortOrder(),
                categoria.getCreatedAt(),
                categoria.getUpdatedAt());
    }
}
