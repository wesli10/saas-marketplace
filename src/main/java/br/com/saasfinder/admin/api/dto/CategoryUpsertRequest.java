package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.DadosDeCategoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Corpo de criacao e de atualizacao de categoria (spec secao 5.2).
 *
 * <p>O corpo do admin traz os **dois** idiomas, ao contrario das rotas publicas
 * (spec secao 5): quem cura escreve o catalogo inteiro, nao uma traducao.
 *
 * <p>Os slugs sao opcionais para caber tambem no PUT, onde nao mudam. Ausentes na
 * criacao, a validacao de tamanho nao dispara - por isso o controller confere a
 * presenca deles antes de mandar para o dominio.
 */
@Schema(
        example =
                """
                {
                  "slug_pt": "atendimento-e-chatbots",
                  "slug_en": "customer-support-and-chatbots",
                  "name_pt": "Atendimento e chatbots",
                  "name_en": "Customer support and chatbots",
                  "description_pt": "Plataformas de atendimento com chatbot e caixa de entrada multicanal.",
                  "description_en": "Support platforms with chatbots and a multichannel inbox.",
                  "parent_id": "01998a4c-6f3e-7c21-9a10-2f6c1d4e8b03",
                  "sort_order": 1
                }
                """)
public record CategoryUpsertRequest(
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "deve ser kebab-case") String slugPt,
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "deve ser kebab-case") String slugEn,
        @NotBlank @Size(max = 80) String namePt,
        @NotBlank @Size(max = 80) String nameEn,
        @Size(max = 500) String descriptionPt,
        @Size(max = 500) String descriptionEn,
        UUID parentId,
        Integer sortOrder) {

    public DadosDeCategoria paraDominio() {
        return new DadosDeCategoria(slugPt, slugEn, namePt, nameEn, descriptionPt, descriptionEn, parentId, sortOrder);
    }
}
