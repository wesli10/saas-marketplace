package br.com.saasfinder.catalog.domain;

import br.com.saasfinder.reviews.domain.NotaDoProduto;
import br.com.saasfinder.search.domain.CategoriaResumo;
import br.com.saasfinder.search.domain.IntegracaoResumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ficha completa de um produto publicado, ja resolvida no idioma pedido
 * (plan.md secao 4.4).
 *
 * <p>`source_note` nao existe aqui, e nao por omissao: a spec secao 4.2 diz que a
 * origem do dado so aparece no admin. Campo que nao existe no record nao vaza por
 * descuido de serializacao.
 *
 * <p>`rating` chega do modulo reviews pela facade dele (spec 003, plano DT-01), e
 * vem `null` quando o produto ainda nao tem review publicada - nota zero seria
 * uma avaliacao pessima, e ausencia de nota e outra coisa.
 *
 * <p>Reaproveita CategoriaResumo e IntegracaoResumo do modulo search - sao o
 * mesmo par {slug, name} do item de lista, e duplicar o record faria duas fontes
 * da verdade para o mesmo pedaco de JSON.
 */
public record ProductDetailView(
        String slug,
        String locale,
        String name,
        String tagline,
        String description,
        String logoUrl,
        String websiteUrl,
        CategoriaResumo category,
        PricingModel pricingModel,
        BigDecimal priceMin,
        String priceCurrency,
        PricePeriod pricePeriod,
        boolean hasFreeTrial,
        boolean hasFreePlan,
        List<String> supportedLanguages,
        String hqCountry,
        List<IntegracaoResumo> integrations,
        List<TagResumo> tags,
        List<String> pros,
        List<String> cons,
        String bestFor,
        List<FaqItem> faq,
        Instant publishedAt,
        Instant updatedAt,
        NotaDoProduto rating) {

    /** A mesma ficha, agora com a nota agregada (spec 003, secao 5.1). */
    public ProductDetailView comNota(NotaDoProduto nota) {
        return new ProductDetailView(
                slug,
                locale,
                name,
                tagline,
                description,
                logoUrl,
                websiteUrl,
                category,
                pricingModel,
                priceMin,
                priceCurrency,
                pricePeriod,
                hasFreeTrial,
                hasFreePlan,
                supportedLanguages,
                hqCountry,
                integrations,
                tags,
                pros,
                cons,
                bestFor,
                faq,
                publishedAt,
                updatedAt,
                nota);
    }
}
