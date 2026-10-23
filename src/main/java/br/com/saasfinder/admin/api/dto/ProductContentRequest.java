package br.com.saasfinder.admin.api.dto;

import br.com.saasfinder.catalog.domain.DadosDeConteudo;
import br.com.saasfinder.catalog.domain.FaqItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corpo de {@code PUT /admin/products/{id}/content/{locale}} (plan.md secao 5.2).
 *
 * <p>Os limites repetem os CHECK da migracao V3: aqui viram 400 com o campo em
 * errors[], la sao a rede de seguranca.
 */
@Schema(
        example =
                """
                {
                  "pros": ["Caixa de entrada unica para WhatsApp e Instagram", "Relatorios por atendente"],
                  "cons": ["Nao tem plano gratuito"],
                  "best_for": "Equipes pequenas de atendimento que atendem por WhatsApp",
                  "faq": [{ "question": "Tem teste gratuito?", "answer": "Sim, 14 dias sem cartao." }]
                }
                """)
public record ProductContentRequest(
        @Size(max = 8) List<@NotBlank @Size(max = 200) String> pros,
        @Size(max = 8) List<@NotBlank @Size(max = 200) String> cons,
        @Size(max = 300) String bestFor,
        @Size(max = 10) List<@Valid FaqItemRequest> faq) {

    /** Item de FAQ com validacao propria - o record de dominio nao carrega anotacao. */
    public record FaqItemRequest(
            @NotBlank @Size(max = 300) String question, @NotBlank @Size(max = 2000) String answer) {}

    public DadosDeConteudo paraDominio() {
        List<FaqItem> perguntas = faq == null
                ? List.of()
                : faq.stream()
                        .map(item -> new FaqItem(item.question(), item.answer()))
                        .toList();

        return new DadosDeConteudo(pros, cons, bestFor, perguntas);
    }
}
