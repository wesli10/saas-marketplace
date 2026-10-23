package br.com.saasfinder.catalog.domain;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Categoria ja resolvida no idioma pedido (spec secao 5).
 *
 * <p>O cliente recebe um slug e um nome, e nao os dois idiomas: quem escolhe e a
 * requisicao. O slug tambem e o do idioma, porque a URL localizada e o que o SEO
 * indexa (spec secao 9).
 *
 * @param productCount publicados nesta categoria e nas subcategorias dela (D-02)
 */
public record CategoryNodeView(
        String slug,
        String name,
        String description,
        int sortOrder,
        long productCount,
        @ArraySchema(schema = @Schema(implementation = CategoryNodeView.class)) List<CategoryNodeView> children) {}
