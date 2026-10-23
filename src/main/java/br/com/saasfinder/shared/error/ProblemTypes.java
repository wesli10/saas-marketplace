package br.com.saasfinder.shared.error;

import org.springframework.http.HttpStatus;

/** Catalogo de tipos de problema da API (plano 001, secao 4.7). */
public final class ProblemTypes {

    /** Parametro `lang` fora de pt-BR e en (AC-09). */
    public static final ProblemType UNSUPPORTED_LOCALE = new ProblemType("unsupported-locale", HttpStatus.BAD_REQUEST);

    /** Parametro invalido: size acima do maximo (AC-15), slugs fora da faixa (AC-16), enum desconhecido. */
    public static final ProblemType INVALID_PARAMETER = new ProblemType("invalid-parameter", HttpStatus.BAD_REQUEST);

    /** Corpo reprovado na validacao, com a lista de campos em errors[] (AC-18). */
    public static final ProblemType VALIDATION_FAILED = new ProblemType("validation-failed", HttpStatus.BAD_REQUEST);

    /** Rota de admin sem credencial ou com credencial errada (AC-17). */
    public static final ProblemType UNAUTHORIZED = new ProblemType("unauthorized", HttpStatus.UNAUTHORIZED);

    /** Recurso inexistente, ou produto nao publicado em rota publica (AC-06). */
    public static final ProblemType NOT_FOUND = new ProblemType("not-found", HttpStatus.NOT_FOUND);

    /**
     * Link de confirmacao que ja foi usado ou passou da validade (spec 003, AC-03 e
     * AC-04).
     *
     * <p>410, e nao 404: o recurso existiu e deixou de existir de proposito, e essa
     * e exatamente a diferenca que a tela usa para oferecer o reenvio em vez de
     * dizer "pagina nao encontrada".
     */
    public static final ProblemType GONE = new ProblemType("gone", HttpStatus.GONE);

    /** Regra de estado violada: slug de publicado (AC-20), categoria com vinculos (AC-21). */
    public static final ProblemType CONFLICT = new ProblemType("conflict", HttpStatus.CONFLICT);

    /**
     * Limite de taxa por IP estourado (spec 003, secao 8 e Q-01).
     *
     * <p>429, e nao 400: o pedido nao esta errado, so e cedo demais.
     */
    public static final ProblemType TOO_MANY_REQUESTS =
            new ProblemType("too-many-requests", HttpStatus.TOO_MANY_REQUESTS);

    /** Publicacao com pendencias, listadas em errors[] (AC-19). */
    public static final ProblemType NOT_PUBLISHABLE =
            new ProblemType("not-publishable", HttpStatus.UNPROCESSABLE_ENTITY);

    /** Falha nao prevista. O detalhe nunca vai para o cliente. */
    public static final ProblemType INTERNAL_ERROR =
            new ProblemType("internal-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private ProblemTypes() {}
}
