package br.com.saasfinder.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produto na visao da curadoria: os **dois** idiomas, o status, o `source_note` e
 * o `published_at` (plan.md secao 5.1).
 *
 * <p>E o oposto da ficha publica, que resolve um idioma so e nunca mostra a
 * origem do dado. Sao duas visoes do mesmo produto porque sao dois leitores
 * diferentes, e misturar as duas e como a origem vazaria.
 *
 * <p>Montada dentro da transacao, e nao no controller: com `open-in-view: false`
 * as colecoes lazy nao sobrevivem a saida do servico.
 */
public record ProductAdminView(
        UUID id,
        String slug,
        String name,
        String taglinePt,
        String taglineEn,
        String descriptionPt,
        String descriptionEn,
        UUID categoryId,
        String websiteUrl,
        String logoUrl,
        PricingModel pricingModel,
        BigDecimal priceMin,
        String priceCurrency,
        PricePeriod pricePeriod,
        boolean hasFreeTrial,
        boolean hasFreePlan,
        List<String> supportedLanguages,
        String hqCountry,
        ProductStatus status,
        Instant publishedAt,
        String sourceNote,
        List<String> integrationSlugs,
        List<String> tagSlugs,
        Instant createdAt,
        Instant updatedAt) {}
