package br.com.saasfinder.reviews.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /reviews/resend} (spec secao 5.1). */
@Schema(example = """
        { "product_slug": "zapier", "email": "ana@acme.com.br" }
        """)
public record ReenvioRequest(@NotBlank String productSlug, @NotBlank @Email @Size(max = 320) String email) {}
