package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.Integration;
import java.time.Instant;
import java.util.UUID;

/**
 * Integracao na visao da curadoria.
 *
 * <p>Sem `product_count`: a contagem e assunto da rota publica, e o curador que
 * precisa dela ja tem `GET /api/v1/integrations`.
 */
public record AdminIntegrationResponse(UUID id, String slug, String name, Instant createdAt, Instant updatedAt) {

    public static AdminIntegrationResponse de(Integration integracao) {
        return new AdminIntegrationResponse(
                integracao.getId(),
                integracao.getSlug(),
                integracao.getName(),
                integracao.getCreatedAt(),
                integracao.getUpdatedAt());
    }
}
