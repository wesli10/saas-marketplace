package br.com.saasfinder.shared.error;

/** Estado atual do recurso nao permite a operacao (AC-20, AC-21). */
public class ConflictException extends ApiException {

    public ConflictException(String chaveDetalhe, Object... argumentos) {
        super(ProblemTypes.CONFLICT, chaveDetalhe, argumentos);
    }
}
