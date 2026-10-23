package br.com.saasfinder.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corpo de criacao de tag: slug estavel e os dois idiomas (constituicao secao 5). */
@Schema(example = "{\"slug\": \"ia-generativa\", \"name_pt\": \"IA generativa\", \"name_en\": \"Generative AI\"}")
public record TagCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "deve ser kebab-case") String slug,
        @NotBlank @Size(max = 80) String namePt,
        @NotBlank @Size(max = 80) String nameEn) {}
