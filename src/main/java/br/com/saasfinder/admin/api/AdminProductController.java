package br.com.saasfinder.admin.api;

import br.com.saasfinder.admin.api.dto.AdminProductListResponse;
import br.com.saasfinder.admin.api.dto.ProductContentRequest;
import br.com.saasfinder.admin.api.dto.ProductUpsertRequest;
import br.com.saasfinder.catalog.domain.CatalogWriteService;
import br.com.saasfinder.catalog.domain.PaginaDeProdutos;
import br.com.saasfinder.catalog.domain.ProductAdminView;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.error.UnsupportedLocaleException;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Curadoria de produtos (spec secao 5.2).
 *
 * <p>Ao contrario da rota publica, esta enxerga os tres status: o curador precisa
 * ver justamente o que ainda nao esta no ar.
 */
@Tag(
        name = "Curadoria: produtos",
        description = "Cadastro, conteudo curado e ciclo de publicacao. Exige credencial de curadoria")
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final CatalogWriteService catalogo;

    public AdminProductController(CatalogWriteService catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(
            summary = "Lista produtos de todos os status",
            description = "Sem `status`, traz os tres. Ordenada por `updated_at desc`: quem cura quer"
                    + " ver o que mexeu por ultimo. A resposta traz os dois idiomas, o `source_note` e"
                    + " o `published_at`, que a rota publica nunca mostra.")
    @ApiResponse(responseCode = "200", description = "Pagina de produtos")
    @GetMapping
    public AdminProductListResponse listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PagingParams paginacao = PagingParams.de(page, size);
        PaginaDeProdutos pagina = catalogo.listarProdutos(statusValido(status), paginacao);

        return new AdminProductListResponse(pagina.itens(), paginacao.page(), paginacao.size(), pagina.total());
    }

    @Operation(summary = "Um produto pelo id, na visao da curadoria")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @GetMapping("/{id}")
    public ProductAdminView porId(@PathVariable UUID id) {
        return catalogo.produtoPorId(id);
    }

    @Operation(
            summary = "Cria um produto em DRAFT",
            description = "Produto nasce sempre em rascunho; publicar e um passo a parte. O que a"
                    + " publicacao vai exigir - preco coerente com o modelo, conteudo nos dois locales -"
                    + " **nao** e exigido aqui: rascunho incompleto e o fluxo normal do curador (D-04)."
                    + " `source_note`, esse sim, e obrigatorio desde o rascunho.")
    @ApiResponse(responseCode = "201", description = "Produto criado, em DRAFT")
    @ApiResponse(responseCode = "409", description = "Slug ja em uso", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductAdminView criar(@Valid @RequestBody ProductUpsertRequest corpo) {
        return catalogo.criarProduto(corpo.paraDominio());
    }

    @Operation(
            summary = "Atualiza um produto",
            description = "Os vinculos sao **substituidos**, nao acumulados: o corpo descreve o produto"
                    + " inteiro. Trocar o slug de um produto PUBLISHED responde 409 (AC-20); em DRAFT,"
                    + " ainda da para corrigir.")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @ApiResponse(responseCode = "409", description = "Slug de publicado, ou slug ja em uso", content = @Content)
    @PutMapping("/{id}")
    public ProductAdminView atualizar(@PathVariable UUID id, @Valid @RequestBody ProductUpsertRequest corpo) {
        return catalogo.atualizarProduto(id, corpo.paraDominio());
    }

    /**
     * Substitui o conteudo curado de um idioma (plan.md secao 5.2).
     *
     * <p>Devolve 204: o corpo enviado ja e a representacao do que ficou gravado, e
     * repeti-lo na resposta nao acrescenta nada.
     */
    @Operation(
            summary = "Substitui o conteudo curado de um idioma",
            description = "Substituicao, e nao remendo: o corpo descreve o registro daquele locale por"
                    + " inteiro, entao item que sumiu do corpo sumiu do produto. `locale` aceita apenas"
                    + " `pt-BR` e `en`.")
    @ApiResponse(responseCode = "204", description = "Conteudo gravado")
    @ApiResponse(
            responseCode = "400",
            description = "Locale nao suportado, ou corpo fora dos limites",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Produto desconhecido", content = @Content)
    @PutMapping("/{id}/content/{locale}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void substituirConteudo(
            @PathVariable UUID id, @PathVariable String locale, @Valid @RequestBody ProductContentRequest corpo) {

        catalogo.substituirConteudo(id, idiomaValido(locale), corpo.paraDominio());
    }

    @Operation(
            summary = "Publica o produto",
            description = "So publica se nao houver pendencia. `published_at` e setado na **primeira**"
                    + " vez e nao e reescrito depois. Publicar o que ja esta publicado e aceito e nao"
                    + " muda a data; publicar o que esta arquivado responde 409.")
    @ApiResponse(responseCode = "200", description = "Produto publicado")
    @ApiResponse(responseCode = "409", description = "Transicao invalida a partir do estado atual", content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Pendencias de completude, todas listadas de uma vez em errors[]",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            examples =
                                    @ExampleObject(
                                            value =
                                                    """
                                                    {
                                                      "type": "https://saasfinder.com.br/problems/not-publishable",
                                                      "title": "Produto nao pode ser publicado",
                                                      "status": 422,
                                                      "detail": "O produto tem pendencias que impedem a publicacao.",
                                                      "instance": "/api/v1/admin/products/0199.../publish",
                                                      "errors": [
                                                        { "field": "product_content.en", "code": "Missing", "message": "Falta o conteudo curado no idioma \\"en\\"." },
                                                        { "field": "price_min", "code": "Missing", "message": "O modelo de cobranca \\"SUBSCRIPTION\\" exige um preco minimo." }
                                                      ]
                                                    }
                                                    """)))
    @PostMapping("/{id}/publish")
    public ProductAdminView publicar(@PathVariable UUID id) {
        return catalogo.publicar(id);
    }

    @Operation(
            summary = "Tira o produto do ar",
            description = "PUBLISHED para DRAFT. O `published_at` fica onde esta: se o produto voltar,"
                    + " continua sendo a data em que apareceu pela primeira vez.")
    @ApiResponse(responseCode = "200", description = "Produto de volta a DRAFT")
    @ApiResponse(responseCode = "409", description = "O produto nao estava publicado", content = @Content)
    @PostMapping("/{id}/unpublish")
    public ProductAdminView despublicar(@PathVariable UUID id) {
        return catalogo.despublicar(id);
    }

    @Operation(
            summary = "Arquiva o produto",
            description = "De qualquer estado para ARCHIVED. Some da rota publica na hora.")
    @ApiResponse(responseCode = "200", description = "Produto arquivado")
    @PostMapping("/{id}/archive")
    public ProductAdminView arquivar(@PathVariable UUID id) {
        return catalogo.arquivar(id);
    }

    /** Mesmo 400 do parametro `lang` das rotas publicas (AC-09), aqui na rota. */
    private AppLocale idiomaValido(String locale) {
        return AppLocale.porCodigo(locale).orElseThrow(() -> new UnsupportedLocaleException(locale));
    }

    /** Status desconhecido vira 400, e nao lista vazia: erro de digitacao e erro. */
    private ProductStatus statusValido(String pedido) {
        if (pedido == null || pedido.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(pedido);
        } catch (IllegalArgumentException desconhecido) {
            throw new InvalidParameterException("problema.invalid-parameter.detalhe", "status");
        }
    }
}
