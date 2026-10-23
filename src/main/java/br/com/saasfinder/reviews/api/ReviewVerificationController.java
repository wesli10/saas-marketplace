package br.com.saasfinder.reviews.api;

import br.com.saasfinder.reviews.domain.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirmacao e reenvio do link (spec secao 5.1).
 *
 * <p>Rotas separadas do {@link ReviewController} porque nao pendem de um produto:
 * quem confirma tem um token na mao, e nao um slug.
 */
@Tag(name = "Reviews", description = "Envio e verificacao de avaliacoes de quem usa o produto")
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewVerificationController {

    private final ReviewService reviews;

    public ReviewVerificationController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Operation(
            summary = "Confirma o e-mail de uma avaliacao",
            description =
                    """
                    Leva a review de `PENDING_EMAIL` para `PENDING_MODERATION`, e o token deixa de
                    valer no mesmo instante.

                    **410 nos tres casos** em que o link nao serve: ja usado, fora da validade de
                    48 h, ou desconhecido. Distinguir um do outro diria, a quem chuta tokens,
                    quais deles um dia existiram - e quem confirmou de verdade nao precisa da
                    distincao, porque a tela oferece o reenvio nos tres.
                    """)
    @ApiResponse(responseCode = "200", description = "E-mail confirmado; a avaliacao foi para a fila de curadoria")
    @ApiResponse(
            responseCode = "410",
            description = "Link ja usado, expirado ou inexistente",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/confirm")
    public void confirmar(@Valid @RequestBody ConfirmacaoRequest corpo) {
        reviews.confirmar(corpo.token());
    }

    @Operation(
            summary = "Reenvia o link de confirmacao",
            description =
                    """
                    Gera um token novo e **invalida o anterior**: dois links validos ao mesmo tempo
                    dobrariam a janela de ataque sem ajudar ninguem.

                    **Responde 202 sempre**, pelo mesmo motivo do envio: produto desconhecido,
                    e-mail que nunca avaliou e avaliacao ja confirmada terminam todos em silencio.
                    Uma resposta diferente transformaria esta rota numa sonda de "este e-mail
                    avaliou este produto?".
                    """)
    @ApiResponse(responseCode = "202", description = "Pedido aceito. Se havia o que reenviar, o e-mail saiu")
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviar(@Valid @RequestBody ReenvioRequest corpo) {
        reviews.reenviar(corpo.productSlug(), corpo.email());
    }
}
