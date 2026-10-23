package br.com.saasfinder.catalog.api.dto;

import br.com.saasfinder.search.domain.MatchedBy;
import java.math.BigDecimal;
import java.util.List;

/**
 * Eco dos filtros efetivamente aplicados (spec secao 5.1).
 *
 * <p>Serve para o cliente montar os chips de filtro sem reinterpretar a propria
 * requisicao, e para depurar: se o resultado surpreende, o eco mostra o que a
 * API entendeu.
 *
 * <p>`matched_by` sai nulo quando a consulta nao tem texto: sem texto nao houve
 * casamento a relatar.
 */
public record ProductQueryEcho(
        String q,
        MatchedBy matchedBy,
        String category,
        List<String> integration,
        List<String> pricingModel,
        Boolean hasFreeTrial,
        Boolean hasFreePlan,
        BigDecimal priceMax) {}
