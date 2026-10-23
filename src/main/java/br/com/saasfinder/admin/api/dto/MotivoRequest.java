package br.com.saasfinder.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O motivo de uma decisao de moderacao (spec secao 5.2 e 7.1).
 *
 * <p>Obrigatorio nas duas rotas que o pedem. Decisao sem motivo registrado e o
 * mesmo que decisao sem analise, do ponto de vista de quem for cobrar depois.
 */
@Schema(example = """
        { "motivo": "texto sem relacao com o produto avaliado" }
        """)
public record MotivoRequest(@NotBlank @Size(max = 300) String motivo) {}
