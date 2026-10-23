package br.com.saasfinder.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz excecao em resposta RFC 9457 (application/problem+json), num lugar so.
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que os erros levantados
 * pelo proprio Spring - rota inexistente, corpo invalido, tipo errado em query -
 * saiam no mesmo formato dos erros de dominio, em vez de dois formatos
 * diferentes na mesma API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemas;

    public GlobalExceptionHandler(ProblemDetailFactory problemas) {
        this.problemas = problemas;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> tratarApiException(ApiException excecao, HttpServletRequest requisicao) {
        ProblemDetail corpo = problemas.montar(
                excecao.getTipo(),
                problemas.texto(excecao.getChaveDetalhe(), excecao.getArgumentos()),
                requisicao.getRequestURI(),
                excecao.getErros(),
                excecao.getExtensoes());

        return ResponseEntity.status(excecao.getTipo().status()).body(corpo);
    }

    /**
     * Como o handler de {@link ApiException}, mais o {@code Retry-After}.
     *
     * <p>Existe separado porque o cabecalho e o que diferencia um 429 util de um
     * 429 que so diz "nao": sem ele, o cliente tenta de novo na hora.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> tratarExcessoDeRequisicoes(
            TooManyRequestsException excecao, HttpServletRequest requisicao) {

        ProblemDetail corpo = problemas.montar(
                excecao.getTipo(),
                problemas.texto(excecao.getChaveDetalhe(), excecao.getArgumentos()),
                requisicao.getRequestURI(),
                excecao.getErros(),
                excecao.getExtensoes());

        return ResponseEntity.status(excecao.getTipo().status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(excecao.segundosDeEspera()))
                .body(corpo);
    }

    /** Ultima barreira: nada de vazar mensagem interna para o cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarInesperado(Exception excecao, HttpServletRequest requisicao) {
        log.error("falha nao tratada em {}", requisicao.getRequestURI(), excecao);

        ProblemDetail corpo = problemas.montar(
                ProblemTypes.INTERNAL_ERROR,
                problemas.texto("problema.internal-error.detalhe"),
                requisicao.getRequestURI(),
                List.of());

        return ResponseEntity.status(ProblemTypes.INTERNAL_ERROR.status()).body(corpo);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException excecao,
            HttpHeaders cabecalhos,
            HttpStatusCode status,
            WebRequest requisicao) {

        List<ApiFieldError> erros = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ApiFieldError(
                        emSnakeCase(erro.getField()),
                        erro.getCode() == null ? "Invalid" : erro.getCode(),
                        erro.getDefaultMessage() == null ? "" : erro.getDefaultMessage()))
                .toList();

        ProblemDetail corpo = problemas.montar(
                ProblemTypes.VALIDATION_FAILED,
                problemas.texto("problema.validation-failed.detalhe"),
                caminho(requisicao),
                erros);

        return ResponseEntity.status(ProblemTypes.VALIDATION_FAILED.status()).body(corpo);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            org.springframework.beans.TypeMismatchException excecao,
            HttpHeaders cabecalhos,
            HttpStatusCode status,
            WebRequest requisicao) {

        String parametro = excecao instanceof MethodArgumentTypeMismatchException erro ? erro.getName() : "";

        ProblemDetail corpo = problemas.montar(
                ProblemTypes.INVALID_PARAMETER,
                problemas.texto("problema.invalid-parameter.detalhe", parametro),
                caminho(requisicao),
                List.of());

        return ResponseEntity.status(ProblemTypes.INVALID_PARAMETER.status()).body(corpo);
    }

    /**
     * Demais erros do proprio Spring (rota inexistente, metodo nao suportado,
     * corpo ilegivel). O status vem do framework; aqui so se acrescenta type,
     * title traduzido e instance.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception excecao,
            Object corpoOriginal,
            HttpHeaders cabecalhos,
            HttpStatusCode status,
            WebRequest requisicao) {

        ProblemType tipo = porStatus(status);
        String detalhe =
                excecao instanceof ErrorResponseException erro && erro.getBody().getDetail() != null
                        ? erro.getBody().getDetail()
                        : problemas.texto(tipo.chaveTitulo());

        ProblemDetail corpo = problemas.montar(tipo, detalhe, caminho(requisicao), List.of());

        return ResponseEntity.status(status).body(corpo);
    }

    /**
     * O Bean Validation reporta o nome da propriedade Java (taglineEn), mas o
     * cliente mandou o corpo em snake_case (tagline_en, desvio D-07). Devolver o
     * nome Java obrigaria quem consome a traduzir de volta para achar o campo.
     */
    private String emSnakeCase(String campo) {
        return campo.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private ProblemType porStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ProblemTypes.NOT_FOUND;
            case 401 -> ProblemTypes.UNAUTHORIZED;
            case 409 -> ProblemTypes.CONFLICT;
            case 422 -> ProblemTypes.NOT_PUBLISHABLE;
            case 400, 405, 406, 415 -> ProblemTypes.INVALID_PARAMETER;
            default -> ProblemTypes.INTERNAL_ERROR;
        };
    }

    private String caminho(WebRequest requisicao) {
        String descricao = requisicao.getDescription(false);
        return descricao.startsWith("uri=") ? descricao.substring(4) : descricao;
    }
}
