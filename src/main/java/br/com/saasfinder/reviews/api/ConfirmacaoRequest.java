package br.com.saasfinder.reviews.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /reviews/confirm} (spec secao 5.1). */
@Schema(example = """
        { "token": "x3B4-rg7KGfsjhRGWn2t2YsxK2HtuAf18cs1J2p17kQ" }
        """)
public record ConfirmacaoRequest(@NotBlank String token) {}
