package br.com.saasfinder.search.domain;

/**
 * Como o resultado foi encontrado (spec secao 5.1, desvio D-06).
 *
 * <p>Torna o caminho da busca observavel pela API, em vez de verificavel so por
 * inspecao de banco - e e o que deixa o fallback do 001-T17 testavel de fora.
 */
public enum MatchedBy {
    /** Casou no indice de full-text do idioma pedido. */
    FULL_TEXT,

    /** O full-text nao achou nada e a similaridade no nome resgatou (001-T17). */
    TRIGRAM,

    /** Havia texto de busca e nada casou, nem por full-text nem por similaridade. */
    NONE
}
