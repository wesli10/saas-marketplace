package br.com.saasfinder.catalog.api;

import br.com.saasfinder.catalog.api.dto.ProductCompareResponse;
import br.com.saasfinder.catalog.api.dto.ProductListResponse;
import br.com.saasfinder.catalog.api.dto.ProductQueryEcho;
import br.com.saasfinder.catalog.domain.CatalogFacade;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.ProductDetailView;
import br.com.saasfinder.search.domain.ProductSearchQuery;
import br.com.saasfinder.search.domain.SearchFacade;
import br.com.saasfinder.search.domain.SearchResult;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.locale.RequestLocale;
import br.com.saasfinder.shared.web.PagingParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas publicas de produto (spec secao 5.1).
 *
 * <p>A rota vive no modulo catalog e a consulta e delegada ao modulo search pela
 * facade: search e motor, nao superficie da API.
 */
@Tag(name = "Produtos", description = "Busca, ficha e comparacao de produtos publicados")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    /** Faixa de slugs aceita pela comparacao (spec secao 5.1). */
    private static final int MINIMO_PARA_COMPARAR = 2;

    private static final int MAXIMO_PARA_COMPARAR = 3;

    private final SearchFacade busca;
    private final CatalogFacade catalogo;

    public ProductController(SearchFacade busca, CatalogFacade catalogo) {
        this.busca = busca;
        this.catalogo = catalogo;
    }

    @Operation(
            summary = "Lista e busca produtos publicados",
            description =
                    """
                    Sem `q`, lista por `updated_at desc`. Com `q`, busca no vetor de full-text do
                    idioma pedido e ordena por relevancia; se o full-text nao achar nada, uma
                    segunda consulta tenta similaridade no nome (limiar 0.3).

                    `query.matched_by` diz por qual caminho o resultado veio: `FULL_TEXT`,
                    `TRIGRAM` ou `NONE`. Sem `q` na requisicao ele sai nulo, porque nao houve
                    casamento a relatar.

                    Filtros combinam com E entre si; `integration` repetido exige **todas** as
                    integracoes, `pricing_model` repetido aceita **qualquer uma**.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Pagina de resultados, com o eco dos filtros aplicados",
            content =
                    @Content(
                            mediaType = "application/json",
                            // Sem `schema` explicito o springdoc nao infere nada: `@Content`
                            // declarado a mao substitui o que ele deduziria, e a resposta
                            // ficava sem tipo no documento - so com o exemplo.
                            schema = @Schema(implementation = ProductListResponse.class),
                            examples =
                                    @ExampleObject(
                                            name = "busca com resultado",
                                            value =
                                                    """
                                                    {
                                                      "locale": "pt-BR",
                                                      "items": [
                                                        {
                                                          "slug": "acme-chat",
                                                          "name": "Acme Chat",
                                                          "tagline": "Atendimento ao cliente no WhatsApp com chatbot",
                                                          "logo_url": null,
                                                          "category": { "slug": "atendimento-e-chatbots", "name": "Atendimento e chatbots" },
                                                          "pricing_model": "SUBSCRIPTION",
                                                          "price_min": 99.00,
                                                          "price_currency": "BRL",
                                                          "price_period": "MONTH",
                                                          "has_free_trial": true,
                                                          "has_free_plan": false,
                                                          "integrations": [{ "slug": "whatsapp", "name": "WhatsApp" }]
                                                        }
                                                      ],
                                                      "page": 0,
                                                      "size": 20,
                                                      "total": 1,
                                                      "query": {
                                                        "q": "atendimento",
                                                        "matched_by": "FULL_TEXT",
                                                        "category": null,
                                                        "integration": [],
                                                        "pricing_model": [],
                                                        "has_free_trial": null,
                                                        "has_free_plan": null,
                                                        "price_max": null
                                                      }
                                                    }
                                                    """)))
    @GetMapping
    public ProductListResponse listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> integration,
            @RequestParam(name = "pricing_model", required = false) List<String> pricingModel,
            @RequestParam(name = "has_free_trial", required = false) Boolean hasFreeTrial,
            @RequestParam(name = "has_free_plan", required = false) Boolean hasFreePlan,
            @RequestParam(name = "price_max", required = false) BigDecimal priceMax) {

        AppLocale idioma = RequestLocale.atual();
        PagingParams paginacao = PagingParams.de(page, size);

        Set<UUID> categorias = category == null ? Set.of() : catalogo.idsDaCategoriaEDescendentes(category);
        List<String> integracoes = integration == null ? List.of() : integration;
        List<String> modelos = validarModelos(pricingModel);

        SearchResult resultado = busca.buscar(new ProductSearchQuery(
                idioma, paginacao, q, categorias, integracoes, modelos, hasFreeTrial, hasFreePlan, priceMax));

        return new ProductListResponse(
                idioma.codigo(),
                // A nota vem do modulo reviews, numa consulta so para a pagina (AC-14).
                catalogo.comNotas(resultado.itens()),
                paginacao.page(),
                paginacao.size(),
                resultado.total(),
                new ProductQueryEcho(
                        q, resultado.matchedBy(), category, integracoes, modelos, hasFreeTrial, hasFreePlan, priceMax));
    }

    /**
     * Comparacao de 2 a 3 produtos (AC-16).
     *
     * <p>A rota vem antes de `/{slug}` so por leitura: o Spring ja prefere o
     * caminho literal ao template, entao "compare" nunca cai na ficha.
     */
    @Operation(
            summary = "Compara de 2 a 3 produtos lado a lado",
            description = "Mesma ficha da rota individual, em array e na ordem pedida. Slug que nao existe"
                    + " (ou que aponta para rascunho/arquivado) derruba a resposta inteira com 404,"
                    + " e a extensao `missing_slugs` diz quais faltaram.")
    @ApiResponse(responseCode = "200", description = "As fichas, na ordem em que os slugs foram pedidos")
    @ApiResponse(
            responseCode = "404",
            description = "Ao menos um slug nao corresponde a produto publicado",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    {
                                                      "type": "https://saasfinder.com.br/problems/not-found",
                                                      "title": "Recurso nao encontrado",
                                                      "status": 404,
                                                      "detail": "Nao existe produto publicado com os slugs: nao-existe.",
                                                      "instance": "/api/v1/products/compare",
                                                      "missing_slugs": ["nao-existe"]
                                                    }
                                                    """)))
    @GetMapping("/compare")
    public ProductCompareResponse comparar(@RequestParam(required = false) List<String> slugs) {
        AppLocale idioma = RequestLocale.atual();
        List<String> pedidos = slugs == null ? List.of() : slugs;

        // A faixa e validada na borda: fora dela nao ha consulta a fazer, e a spec
        // pede 400, nao uma lista curta ou gigante.
        if (pedidos.size() < MINIMO_PARA_COMPARAR || pedidos.size() > MAXIMO_PARA_COMPARAR) {
            throw new InvalidParameterException("problema.invalid-parameter.detalhe", "slugs");
        }

        return new ProductCompareResponse(idioma.codigo(), catalogo.produtosPublicadosPorSlugs(pedidos, idioma));
    }

    /**
     * Ficha completa (AC-06, AC-07).
     *
     * <p>Vai para o catalogo, e nao para a busca: aqui nao ha consulta a montar,
     * so um agregado a ler pelo slug.
     */
    @Operation(
            summary = "Ficha completa de um produto publicado",
            description = "Resolvida no idioma pedido: `tagline`, `description`, `pros`, `cons`, `best_for` e"
                    + " `faq` ja vem num idioma so. Produto sem conteudo curado no idioma responde"
                    + " com os campos longos vazios, e nao com erro. `source_note` nunca aparece aqui.")
    @ApiResponse(responseCode = "200", description = "Ficha do produto")
    @ApiResponse(
            responseCode = "404",
            description = "Slug desconhecido, ou produto em DRAFT/ARCHIVED - que nao existem para o publico",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{slug}")
    public ProductDetailView porSlug(@PathVariable String slug) {
        return catalogo.produtoPublicadoPorSlug(slug, RequestLocale.atual());
    }

    /** Modelo desconhecido vira 400, e nao lista vazia: erro de digitacao deve aparecer como erro. */
    private List<String> validarModelos(List<String> pedidos) {
        if (pedidos == null) {
            return List.of();
        }
        for (String modelo : pedidos) {
            try {
                PricingModel.valueOf(modelo);
            } catch (IllegalArgumentException desconhecido) {
                throw new InvalidParameterException("problema.invalid-parameter.detalhe", "pricing_model");
            }
        }
        return pedidos;
    }
}
