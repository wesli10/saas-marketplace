package br.com.saasfinder.shared.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Acrescenta a toda operacao o que vale para toda operacao: o corpo de erro em
 * problem+json e, nas rotas de curadoria, a exigencia de credencial.
 *
 * <p>Feito aqui, e nao por anotacao repetida em cada metodo, porque a regra e
 * global - e uma anotacao esquecida num controller novo produziria documentacao
 * que mente sobre o comportamento (AC-23).
 */
@Component
public class OpenApiErrosCustomizer implements OperationCustomizer {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public Operation customize(Operation operacao, HandlerMethod metodo) {
        boolean deCuradoria = metodo.getBeanType().getPackageName().startsWith("br.com.saasfinder.admin");

        if (deCuradoria) {
            operacao.addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.BASIC));
            operacao.getResponses()
                    .addApiResponse(
                            "401",
                            resposta(
                                    "Credencial de curadoria ausente ou invalida",
                                    """
                                    {
                                      "type": "https://saasfinder.com.br/problems/unauthorized",
                                      "title": "Nao autorizado",
                                      "status": 401,
                                      "detail": "Credenciais ausentes ou invalidas.",
                                      "instance": "/api/v1/admin/products"
                                    }
                                    """));
        }

        operacao.getResponses()
                .addApiResponse(
                        "400",
                        resposta(
                                "Parametro ou corpo invalido. `errors[]` nomeia os campos, em snake_case",
                                """
                                {
                                  "type": "https://saasfinder.com.br/problems/validation-failed",
                                  "title": "Requisicao invalida",
                                  "status": 400,
                                  "detail": "Um ou mais campos sao invalidos.",
                                  "instance": "/api/v1/admin/products",
                                  "errors": [
                                    { "field": "tagline_en", "code": "NotBlank", "message": "nao pode estar em branco" }
                                  ]
                                }
                                """));

        operacao.getResponses()
                .addApiResponse(
                        "500",
                        resposta(
                                "Falha inesperada. O detalhe interno nunca vai para o cliente",
                                """
                                {
                                  "type": "https://saasfinder.com.br/problems/internal-error",
                                  "title": "Erro interno",
                                  "status": 500,
                                  "detail": "Ocorreu um erro inesperado.",
                                  "instance": "/api/v1/products"
                                }
                                """));

        return operacao;
    }

    private ApiResponse resposta(String descricao, String exemplo) {
        return new ApiResponse()
                .description(descricao)
                .content(new Content()
                        .addMediaType(
                                PROBLEM_JSON, new MediaType().addExamples("padrao", new Example().value(exemplo))));
    }
}
