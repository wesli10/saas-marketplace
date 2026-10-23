package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.DadosDeProduto;
import br.com.saasfinder.catalog.domain.PricePeriod;
import br.com.saasfinder.catalog.domain.PricingModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de criacao e de atualizacao de produto (plan.md secao 5.1).
 *
 * <p>Os dois idiomas sao obrigatorios (constituicao secao 5): produto so entra no
 * catalogo se der para le-lo em pt e en. O que a publicacao ainda vai exigir -
 * preco coerente com o modelo, conteudo curado nos dois locales - **nao** e
 * exigido aqui, porque rascunho incompleto e o fluxo normal do curador (D-04).
 *
 * <p>`source_note` e obrigatorio desde o rascunho: a constituicao secao 7.5
 * proibe dado de produto "de cabeca", e sem a origem nao da para conferir.
 *
 * <p>Os limites de tamanho repetem os CHECK da migracao V3 de proposito: aqui
 * eles viram 400 com o campo em errors[], la eles sao a rede de seguranca.
 */
@Schema(
        example =
                """
                {
                  "slug": "acme-chat",
                  "name": "Acme Chat",
                  "tagline_pt": "Atendimento ao cliente no WhatsApp com chatbot",
                  "tagline_en": "Customer support on WhatsApp with a chatbot",
                  "description_pt": "Central de atendimento multicanal com chatbot treinado na base da empresa.",
                  "description_en": "Multichannel support inbox with a chatbot trained on the company knowledge base.",
                  "category_id": "01998a4c-6f3e-7c21-9a10-2f6c1d4e8b03",
                  "website_url": "https://acme.com",
                  "logo_url": "https://acme.com/logo.png",
                  "pricing_model": "SUBSCRIPTION",
                  "price_min": 99.00,
                  "price_currency": "BRL",
                  "price_period": "MONTH",
                  "has_free_trial": true,
                  "has_free_plan": false,
                  "supported_languages": ["pt", "en"],
                  "hq_country": "BR",
                  "source_note": "https://acme.com/pricing - coletado em 2026-08-20",
                  "integration_slugs": ["whatsapp", "hubspot"],
                  "tag_slugs": ["chatbot", "atendimento"]
                }
                """)
public record ProductUpsertRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "deve ser kebab-case") String slug,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 160) String taglinePt,
        @NotBlank @Size(max = 160) String taglineEn,
        @NotBlank @Size(max = 4000) String descriptionPt,
        @NotBlank @Size(max = 4000) String descriptionEn,
        @NotNull UUID categoryId,
        @NotBlank @Pattern(regexp = "^https?://.+", message = "deve ser uma URL http(s)") String websiteUrl,
        @Pattern(regexp = "^https?://.+", message = "deve ser uma URL http(s)") String logoUrl,
        @NotNull PricingModel pricingModel,
        @PositiveOrZero BigDecimal priceMin,
        @Pattern(regexp = "^[A-Z]{3}$", message = "deve ser um codigo ISO 4217") String priceCurrency,
        PricePeriod pricePeriod,
        Boolean hasFreeTrial,
        Boolean hasFreePlan,
        List<@Pattern(regexp = "^[a-z]{2}$", message = "deve ser um codigo ISO 639-1") String> supportedLanguages,
        @Pattern(regexp = "^[A-Z]{2}$", message = "deve ser um codigo ISO 3166-1") String hqCountry,
        @NotBlank @Size(max = 500) String sourceNote,
        List<String> integrationSlugs,
        List<String> tagSlugs) {

    public DadosDeProduto paraDominio() {
        return new DadosDeProduto(
                slug,
                name,
                taglinePt,
                taglineEn,
                descriptionPt,
                descriptionEn,
                categoryId,
                websiteUrl,
                logoUrl,
                pricingModel,
                priceMin,
                priceCurrency,
                pricePeriod,
                hasFreeTrial,
                hasFreePlan,
                supportedLanguages,
                hqCountry,
                sourceNote,
                integrationSlugs,
                tagSlugs);
    }
}
