package br.com.saasfinder.admin.api;

import br.com.saasfinder.admin.api.dto.AdminReviewListResponse;
import br.com.saasfinder.admin.api.dto.MotivoRequest;
import br.com.saasfinder.reviews.domain.PaginaDeModeracao;
import br.com.saasfinder.reviews.domain.ReviewModeracaoService;
import br.com.saasfinder.reviews.domain.ReviewModeracaoView;
import br.com.saasfinder.reviews.domain.ReviewStatus;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.web.PagingParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Curadoria de reviews (spec secao 5.2). Protegida por HTTP Basic no
 * {@code SecurityConfig}, junto com todo o {@code /api/v1/admin}.
 *
 * <p>Nao ha tela: a curadoria acontece pela API, como no 001. Uma interface de
 * moderacao e fatia propria, e depende da 006-identidade.
 *
 * <p>Vive no modulo {@code admin}, e nao em {@code reviews.api}, como os
 * controllers de curadoria do catalogo: e o {@code admin} que concentra a
 * superficie da curadoria, e e o pacote dele que o {@code OpenApiErrosCustomizer}
 * usa para exigir credencial e documentar o 401 em toda operacao. Escrito no
 * pacote errado, o controller funcionava e a documentacao mentia - quem apontou
 * foi o {@code OpenApiIT}.
 */
@Tag(name = "Curadoria: reviews", description = "Fila de moderacao e decisoes. Exige credencial de curadoria")
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ReviewModeracaoService moderacao;

    public AdminReviewController(ReviewModeracaoService moderacao) {
        this.moderacao = moderacao;
    }

    @Operation(
            summary = "Lista a fila de moderacao",
            description =
                    """
                    Da mais antiga para a mais recente, porque isto e uma fila: quem esperou mais
                    e atendido antes.

                    Traz o texto completo e o dominio do autor - **nunca o e-mail**. A decisao e
                    sobre o que foi escrito, e o dominio ja diz o que importa para o selo.

                    `outras_do_mesmo_dominio` e o sinal que substituiu o bloqueio por dominio
                    (spec Q-05): cinco avaliacoes de `@acme.com.br` no mesmo produto merecem um
                    olhar, mas barrar a segunda calaria o colega de quem ja avaliou.
                    """)
    @ApiResponse(responseCode = "200", description = "Pagina da fila")
    @ApiResponse(responseCode = "400", description = "`status` ou `product` desconhecido", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sem credencial de curadoria", content = @Content)
    @GetMapping
    public AdminReviewListResponse fila(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PagingParams paginacao = PagingParams.de(page, size);
        PaginaDeModeracao pagina = moderacao.fila(statusValido(status), product, paginacao);

        return new AdminReviewListResponse(pagina.itens(), paginacao.page(), paginacao.size(), pagina.total());
    }

    @Operation(
            summary = "Aprova e publica a avaliacao",
            description = "So a partir de `PENDING_MODERATION`. Avaliacao que ainda nao confirmou o e-mail"
                    + " responde **409**: o pedido esta bem formado, o estado e que nao permite."
                    + " Quem escreveu recebe um e-mail avisando que saiu (spec Q-02).")
    @ApiResponse(responseCode = "200", description = "Publicada")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @ApiResponse(
            responseCode = "409",
            description = "A avaliacao nao esta em `PENDING_MODERATION`",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{id}/approve")
    public ReviewModeracaoView aprovar(@PathVariable UUID id, Principal curador) {
        return moderacao.aprovar(id, curador.getName());
    }

    @Operation(
            summary = "Rejeita a avaliacao, com motivo",
            description = "O motivo e obrigatorio e fica registrado. **Nao** ha e-mail de rejeicao"
                    + " (spec Q-02): ele abriria uma discussao por e-mail que ninguem vai moderar,"
                    + " e a politica publica ja diz o que e recusado.")
    @ApiResponse(responseCode = "200", description = "Rejeitada")
    @ApiResponse(responseCode = "400", description = "Motivo ausente", content = @Content)
    @ApiResponse(responseCode = "409", description = "A avaliacao nao esta em `PENDING_MODERATION`", content = @Content)
    @PostMapping("/{id}/reject")
    public ReviewModeracaoView rejeitar(
            @PathVariable UUID id, @Valid @RequestBody MotivoRequest corpo, Principal curador) {
        return moderacao.rejeitar(id, corpo.motivo(), curador.getName());
    }

    @Operation(
            summary = "Registra a decisao de manter a avaliacao",
            description =
                    """
                    Para depois de uma contestacao (spec secao 7.1): quem foi avaliado reclama, o
                    curador analisa e decide manter. O estado nao muda; o que muda e o registro.

                    Sem esta linha nao ha como demonstrar que houve analise - que e o que o Marco
                    Civil art. 19 cobra de quem publica conteudo de terceiro.

                    So sobre avaliacao publicada: registrar "decidimos manter" sobre uma que nunca
                    foi publicada seria uma mentira na auditoria.
                    """)
    @ApiResponse(responseCode = "200", description = "Decisao registrada")
    @ApiResponse(responseCode = "400", description = "Motivo ausente", content = @Content)
    @ApiResponse(responseCode = "409", description = "A avaliacao nao esta publicada", content = @Content)
    @PostMapping("/{id}/note")
    public ReviewModeracaoView registrarNota(
            @PathVariable UUID id, @Valid @RequestBody MotivoRequest corpo, Principal curador) {
        return moderacao.registrarDecisaoDeManter(id, corpo.motivo(), curador.getName());
    }

    /** Status desconhecido vira 400, e nao lista vazia - como no filtro do catalogo. */
    private ReviewStatus statusValido(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReviewStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            throw new InvalidParameterException("problema.invalid-parameter.detalhe", "status");
        }
    }
}
