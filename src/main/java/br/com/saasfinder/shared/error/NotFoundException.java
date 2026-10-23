package br.com.saasfinder.shared.error;

/** Recurso inexistente. Tambem cobre o produto nao publicado visto de fora (AC-06). */
public class NotFoundException extends ApiException {

    public NotFoundException(String chaveDetalhe, Object... argumentos) {
        super(ProblemTypes.NOT_FOUND, chaveDetalhe, argumentos);
    }
}
