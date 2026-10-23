package br.com.saasfinder.catalog.api.dto;

import br.com.saasfinder.catalog.domain.IntegrationView;
import java.util.List;

/**
 * Envelope de {@code GET /integrations} (plan.md secao 4.6).
 *
 * <p>Mesmo formato de envelope das outras listas, mas sem `locale`: o conteudo
 * daqui nao muda com o idioma.
 */
public record IntegrationListResponse(List<IntegrationView> items) {}
