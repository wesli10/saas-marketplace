package br.com.saasfinder.shared.error;

import java.util.List;

/** Parametro de consulta fora do aceito (AC-15, AC-16). */
public class InvalidParameterException extends ApiException {

    public InvalidParameterException(String chaveDetalhe, Object... argumentos) {
        super(ProblemTypes.INVALID_PARAMETER, chaveDetalhe, argumentos);
    }

    public InvalidParameterException(String chaveDetalhe, List<ApiFieldError> erros, Object... argumentos) {
        super(ProblemTypes.INVALID_PARAMETER, chaveDetalhe, erros, argumentos);
    }
}
