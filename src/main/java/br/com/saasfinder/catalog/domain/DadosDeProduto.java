package br.com.saasfinder.catalog.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Dados de entrada para criar ou atualizar um produto (plan.md secao 5.1).
 *
 * <p>Traz os dois idiomas, como todo corpo de curadoria (spec secao 5), e os
 * vinculos por **slug**: o curador conhece "whatsapp", nao o UUID da integracao.
 *
 * <p>E um tipo do catalog, e nao o DTO da borda, para o modulo admin depender do
 * catalogo e nao o contrario.
 */
public record DadosDeProduto(
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
        Boolean hasFreeTrial,
        Boolean hasFreePlan,
        List<String> supportedLanguages,
        String hqCountry,
        String sourceNote,
        List<String> integrationSlugs,
        List<String> tagSlugs) {}
