package br.com.saasfinder.search.domain;

import br.com.saasfinder.reviews.domain.NotaDoProduto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Item de lista, ja resolvido no idioma pedido (spec secao 5.1).
 *
 * <p>Nao traz `description` nem `source_note`: a primeira e longa demais para
 * uma listagem e a segunda nunca sai para fora (spec secao 4.2).
 *
 * @param id <b>nao sai no JSON</b>. Existe para o catalog pedir as notas da pagina
 *     inteira numa consulta so (spec 003, AC-14): sem o id aqui, seria preciso uma
 *     segunda ida ao banco so para traduzir os slugs de volta em ids que a busca
 *     acabou de ler. A API publica continua falando por slug.
 * @param rating a nota agregada, ou {@code null} quando o produto ainda nao tem
 *     review publicada - nunca um agregado zerado (spec 003, secao 4.4)
 */
public record ProductSummaryView(
        @JsonIgnore UUID id,
        String slug,
        String name,
        String tagline,
        String logoUrl,
        CategoriaResumo category,
        String pricingModel,
        BigDecimal priceMin,
        String priceCurrency,
        String pricePeriod,
        boolean hasFreeTrial,
        boolean hasFreePlan,
        List<IntegracaoResumo> integrations,
        NotaDoProduto rating) {

    /** O mesmo item, agora com a nota. O record e imutavel; isto devolve uma copia. */
    public ProductSummaryView comNota(NotaDoProduto nota) {
        return new ProductSummaryView(
                id,
                slug,
                name,
                tagline,
                logoUrl,
                category,
                pricingModel,
                priceMin,
                priceCurrency,
                pricePeriod,
                hasFreeTrial,
                hasFreePlan,
                integrations,
                nota);
    }
}
