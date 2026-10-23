package br.com.saasfinder.shared.error;

/** Valor de `lang` fora de pt-BR e en (AC-09). */
public class UnsupportedLocaleException extends ApiException {

    public UnsupportedLocaleException(String recebido) {
        super(ProblemTypes.UNSUPPORTED_LOCALE, "problema.unsupported-locale.detalhe", recebido);
    }
}
