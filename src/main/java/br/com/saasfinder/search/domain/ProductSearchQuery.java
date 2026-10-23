package br.com.saasfinder.search.domain;

import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta de produtos publicados (spec secao 5.1).
 *
 * <p>A categoria chega ja resolvida em ids - dela e das subcategorias -, porque
 * traduzir slug em id e assunto do modulo catalog, nao da busca.
 *
 *
 * @param q texto livre; nulo ou em branco significa listagem sem busca
 * @param integrationSlugs combinadas com E: o produto precisa ter todas
 * @param pricingModels combinados com OU: basta um deles
 */
public record ProductSearchQuery(
        AppLocale locale,
        PagingParams paginacao,
        String q,
        Set<UUID> categorias,
        List<String> integrationSlugs,
        List<String> pricingModels,
        Boolean hasFreeTrial,
        Boolean hasFreePlan,
        BigDecimal priceMax) {

    /** Ha texto de busca? Espaco em branco nao conta como busca. */
    public boolean temTexto() {
        return q != null && !q.isBlank();
    }

    public static ProductSearchQuery semFiltros(AppLocale locale, PagingParams paginacao) {
        return new ProductSearchQuery(locale, paginacao, null, Set.of(), List.of(), List.of(), null, null, null);
    }
}
