package br.com.saasfinder.catalog.infra.seed;

import br.com.saasfinder.catalog.domain.PricePeriod;
import br.com.saasfinder.catalog.domain.PricingModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Formato dos arquivos YAML de seed (plan.md DT-15).
 *
 * <p>Sao records proprios, e nao os DTOs da API: o arquivo de seed usa **slug**
 * onde a API usa UUID (`category`, `integrations`, `tags`), porque quem escreve o
 * arquivo a mao nao tem id nenhum em maos.
 */
public final class ArquivosDeSeed {

    private ArquivosDeSeed() {}

    /** Uma categoria do `categories.yml`. `parent` e o slug em portugues da raiz. */
    public record CategoriaDeSeed(
            String slugPt,
            String slugEn,
            String namePt,
            String nameEn,
            String descriptionPt,
            String descriptionEn,
            String parent,
            Integer sortOrder) {}

    public record IntegracaoDeSeed(String slug, String name) {}

    public record TagDeSeed(String slug, String namePt, String nameEn) {}

    public record FaqDeSeed(String question, String answer) {}

    /** Conteudo curado de um idioma, dentro do arquivo do produto. */
    public record ConteudoDeSeed(List<String> pros, List<String> cons, String bestFor, List<FaqDeSeed> faq) {}

    /**
     * Um produto por arquivo (plan.md DT-15): o diff da revisao fica legivel, que
     * e o que a spec secao 6 pede ao exigir revisao humana antes do commit.
     *
     * @param content conteudo por locale, chaveado por "pt-BR" e "en"
     */
    public record ProdutoDeSeed(
            String slug,
            String name,
            String category,
            String taglinePt,
            String taglineEn,
            String descriptionPt,
            String descriptionEn,
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
            List<String> integrations,
            List<String> tags,
            Map<String, ConteudoDeSeed> content) {}
}
