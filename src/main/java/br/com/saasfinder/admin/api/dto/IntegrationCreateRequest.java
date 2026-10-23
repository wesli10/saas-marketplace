package br.com.saasfinder.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de criacao de integracao (spec secao 5.2).
 *
 * <p>`name` e unico, sem par pt/en: nome de integracao e nome proprio (WhatsApp,
 * HubSpot) e nao se traduz.
 */
@Schema(example = "{\"slug\": \"whatsapp\", \"name\": \"WhatsApp\"}")
public record IntegrationCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "deve ser kebab-case") String slug,
        @NotBlank @Size(max = 80) String name) {}
