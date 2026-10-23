package br.com.saasfinder.catalog.api;

import br.com.saasfinder.catalog.api.dto.CategoryTreeResponse;
import br.com.saasfinder.catalog.domain.CatalogFacade;
import br.com.saasfinder.catalog.domain.CategoryDetailView;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.locale.RequestLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Categorias do catalogo (spec secao 5.1). Rotas publicas. */
@Tag(name = "Categorias", description = "Arvore de categorias do catalogo, resolvida no idioma pedido")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CatalogFacade catalogo;

    public CategoryController(CatalogFacade catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(
            summary = "Arvore de categorias",
            description = "Raiz com as subcategorias. `product_count` conta os publicados da categoria"
                    + " **e das filhas dela**, para bater com o filtro `category=` da busca (D-02).")
    @GetMapping
    public CategoryTreeResponse arvore() {
        AppLocale idioma = RequestLocale.atual();
        return new CategoryTreeResponse(idioma.codigo(), catalogo.arvoreDeCategorias(idioma));
    }

    @Operation(
            summary = "Uma categoria pelo slug",
            description = "O slug pode estar em qualquer um dos dois idiomas (AC-04). A resposta traz"
                    + " `alternates` com o slug nos dois idiomas, para quem precisa montar a URL da outra"
                    + " versao (seletor de idioma e hreflang) sem uma segunda chamada.")
    @ApiResponse(responseCode = "200", description = "Categoria encontrada")
    @ApiResponse(
            responseCode = "404",
            description = "Nenhuma categoria responde por esse slug",
            content = @io.swagger.v3.oas.annotations.media.Content)
    @GetMapping("/{slug}")
    public CategoryDetailView porSlug(@PathVariable String slug) {
        return catalogo.categoriaPorSlug(slug, RequestLocale.atual());
    }
}
