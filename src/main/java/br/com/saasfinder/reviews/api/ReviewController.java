package br.com.saasfinder.reviews.api;

import br.com.saasfinder.reviews.domain.PaginaDeReviews;
import br.com.saasfinder.reviews.domain.ReviewService;
import br.com.saasfinder.shared.locale.RequestLocale;
import br.com.saasfinder.shared.web.PagingParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas publicas de review (spec secao 5.1). */
@Tag(name = "Reviews", description = "Envio e verificacao de avaliacoes de quem usa o produto")
@RestController
@RequestMapping("/api/v1/products")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Operation(
            summary = "Envia uma avaliacao do produto",
            description =
                    """
                    Cria a review em `PENDING_EMAIL` e dispara o e-mail com o link de confirmacao.
                    A review **nao** aparece em lugar nenhum antes de o e-mail ser confirmado e de
                    um curador aprovar.

                    **Responde 202 sempre que a entrada e valida** - inclusive quando ja existe
                    review daquele e-mail para aquele produto. Uma resposta diferente contaria,
                    para quem apenas chutou o endereco, que aquela pessoa avaliou aquele produto.

                    O corpo da resposta e vazio de proposito: nao ha nada que possa ser dito sem
                    revelar o que a resposta uniforme existe para esconder.

                    O idioma da review e o da requisicao (`Accept-Language` ou `?lang=`), e e nele
                    que o e-mail sai.
                    """)
    @ApiResponse(responseCode = "202", description = "Avaliacao aceita e em verificacao de e-mail")
    @ApiResponse(
            responseCode = "400",
            description = "Campo invalido, com o nome dele em `errors[]`",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "404", description = "Nao existe produto publicado com este slug", content = @Content)
    @PostMapping("/{slug}/reviews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void criar(@PathVariable String slug, @Valid @RequestBody ReviewRequest corpo) {
        try {
            reviews.registrar(slug, corpo.paraDominio(), RequestLocale.atual());
        } catch (DataIntegrityViolationException colisao) {
            // Dois envios simultaneos do mesmo e-mail para o mesmo produto: o
            // servico ve os dois como primeiros, e o indice unico da V7 recusa o
            // segundo. A transacao ja foi desfeita - nao ha review duplicada -, e a
            // resposta tem de continuar identica a do primeiro envio (AC-05).
            //
            // Fica aqui, e nao no servico, porque dentro da transacao seria tarde:
            // capturar a violacao ali deixaria a transacao marcada para rollback de
            // qualquer jeito.
        }
    }

    @Operation(
            summary = "Lista as avaliacoes publicadas do produto",
            description =
                    """
                    So `PUBLISHED`, da mais recente para a mais antiga. Avaliacao aguardando
                    confirmacao de e-mail ou curadoria nao aparece aqui nem em lugar nenhum.

                    `rating` e o agregado de **todas** as publicadas, e nao das que couberam na
                    pagina. Vem `null` - e nao zerado - quando o produto ainda nao tem nenhuma:
                    nota zero e uma avaliacao pessima, ausencia de nota e outra coisa.

                    A media considera as avaliacoes dos **dois idiomas** (spec secao 4.3): a
                    opiniao de quem escreveu em ingles conta igual, e cada item traz o `locale`
                    em que foi escrito para a tela poder marcar a diferenca.

                    Nenhum campo de moderacao ou de verificacao sai por aqui.
                    """)
    @ApiResponse(responseCode = "200", description = "Pagina de avaliacoes publicadas, com o agregado")
    @ApiResponse(responseCode = "404", description = "Nao existe produto publicado com este slug", content = @Content)
    @GetMapping("/{slug}/reviews")
    public ReviewListResponse listar(
            @PathVariable String slug,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PagingParams paginacao = PagingParams.de(page, size);
        PaginaDeReviews pagina = reviews.publicadasDe(slug, paginacao);

        return new ReviewListResponse(
                pagina.nota(),
                pagina.itens().stream().map(ReviewView::de).toList(),
                paginacao.page(),
                paginacao.size(),
                pagina.total());
    }
}
