package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.Tag;
import java.time.Instant;
import java.util.UUID;

/** Tag como a curadoria a enxerga: os dois idiomas de uma vez (spec secao 5). */
public record AdminTagResponse(
        UUID id, String slug, String namePt, String nameEn, Instant createdAt, Instant updatedAt) {

    public static AdminTagResponse de(Tag tag) {
        return new AdminTagResponse(
                tag.getId(), tag.getSlug(), tag.getNamePt(), tag.getNameEn(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
