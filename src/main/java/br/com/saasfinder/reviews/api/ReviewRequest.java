package br.com.saasfinder.reviews.api;

import br.com.saasfinder.reviews.domain.DadosDeReview;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /products/'{'slug'}'/reviews} (spec secao 5.1).
 *
 * <p>A validacao acontece aqui, na borda: o dominio recebe dado valido
 * (constituicao secao 6). Os limites sao os mesmos CHECKs da V7 - o banco e a
 * ultima linha, nao a primeira.
 *
 * <p>O idioma <b>nao</b> vem no corpo: ele e o da pagina onde a review foi escrita
 * (spec secao 4.3), resolvido pelo filtro de idioma a partir de `Accept-Language`
 * ou `?lang=`. Deixar quem envia escolher permitiria uma review em portugues
 * marcada como inglesa.
 */
@Schema(
        example =
                """
                {
                  "rating": 4,
                  "would_recommend": true,
                  "title": "Resolveu o atendimento do time",
                  "body": "Usamos ha oito meses no suporte e o tempo de primeira resposta caiu pela metade. A configuracao inicial levou uma tarde.",
                  "author_name": "Ana Ribeiro",
                  "author_email": "ana@acme.com.br"
                }
                """)
public record ReviewRequest(
        @NotNull @Min(1) @Max(5) @Schema(description = "Nota inteira de 1 a 5, sem meia-estrela") Integer rating,
        @NotNull @Schema(description = "Recomendaria o produto? Obrigatorio: e insumo do ranking (spec 004)")
                Boolean wouldRecommend,
        @NotBlank @Size(max = 120) String title,
        @NotBlank
                @Size(min = 80, max = 2000)
                @Schema(description = "O piso de 80 caracteres barra `bom` e `nao gostei`, que nao ajudam a decidir")
                String body,
        @NotBlank @Size(max = 80) @Schema(description = "Nome de exibicao, livre") String authorName,
        @NotBlank @Email @Size(max = 320) @Schema(description = "E-mail de trabalho. Nunca aparece em rota publica")
                String authorEmail) {

    public DadosDeReview paraDominio() {
        return new DadosDeReview(rating, wouldRecommend, title, body, authorName, authorEmail);
    }
}
