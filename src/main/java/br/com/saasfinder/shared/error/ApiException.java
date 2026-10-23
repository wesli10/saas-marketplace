package br.com.saasfinder.shared.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excecao de dominio que ja carrega o tipo de problema.
 *
 * <p>Controllers nao capturam nada: quem traduz isto em resposta HTTP e o
 * {@link GlobalExceptionHandler}, num lugar so (constituicao secao 6).
 */
public class ApiException extends RuntimeException {

    private final transient ProblemType tipo;
    private final String chaveDetalhe;
    private final transient Object[] argumentos;
    private final transient List<ApiFieldError> erros;

    /** Campos extras do corpo RFC 9457, alem de errors[]. Ex.: missing_slugs (AC-16). */
    private final transient Map<String, Object> extensoes = new LinkedHashMap<>();

    public ApiException(ProblemType tipo, String chaveDetalhe, Object... argumentos) {
        this(tipo, chaveDetalhe, List.of(), argumentos);
    }

    public ApiException(ProblemType tipo, String chaveDetalhe, List<ApiFieldError> erros, Object... argumentos) {
        super(chaveDetalhe);
        this.tipo = tipo;
        this.chaveDetalhe = chaveDetalhe;
        this.argumentos = argumentos;
        this.erros = List.copyOf(erros);
    }

    public ProblemType getTipo() {
        return tipo;
    }

    public String getChaveDetalhe() {
        return chaveDetalhe;
    }

    public Object[] getArgumentos() {
        return argumentos.clone();
    }

    public List<ApiFieldError> getErros() {
        return erros;
    }

    /**
     * Acrescenta um campo ao corpo do problema e devolve a propria excecao, para
     * poder ser lancada na mesma expressao.
     *
     * <p>A RFC 9457 preve extensoes justamente para isto: dizer *qual* slug faltou
     * sem obrigar o cliente a interpretar o texto do detalhe, que muda com o idioma.
     */
    public ApiException comExtensao(String chave, Object valor) {
        extensoes.put(chave, valor);
        return this;
    }

    public Map<String, Object> getExtensoes() {
        return Map.copyOf(extensoes);
    }
}
