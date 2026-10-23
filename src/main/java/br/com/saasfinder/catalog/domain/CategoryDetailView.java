package br.com.saasfinder.catalog.domain;

import java.util.List;
import java.util.Map;

/**
 * Resposta de {@code GET /categories/{slug}}.
 *
 * <p>E a {@link CategoryNodeView} mais {@code alternates}, e nao o mesmo record
 * com um campo a mais: {@code default-property-inclusion: always} faria o campo
 * nulo aparecer como {@code "alternates": null} em cada no de
 * {@code GET /categories}, poluindo a rota que nao precisa dele.
 *
 * <p>{@code alternates} existe porque o slug de categoria muda com o idioma, e
 * quem monta a URL da outra versao - seletor de idioma e {@code hreflang} -
 * precisaria de uma segunda chamada para descobrir o par. Slug de produto nao
 * muda (spec 001 secao 4.2), por isso a ficha de produto nao ganha o campo.
 */
public record CategoryDetailView(
        String slug,
        String name,
        String description,
        int sortOrder,
        long productCount,
        List<CategoryNodeView> children,
        Map<String, String> alternates) {

    /** Mesma categoria, no idioma ja resolvido, mais o par de slugs. */
    public static CategoryDetailView de(CategoryNodeView no, String slugPt, String slugEn) {
        return new CategoryDetailView(
                no.slug(),
                no.name(),
                no.description(),
                no.sortOrder(),
                no.productCount(),
                no.children(),
                Map.of("pt-BR", slugPt, "en", slugEn));
    }
}
