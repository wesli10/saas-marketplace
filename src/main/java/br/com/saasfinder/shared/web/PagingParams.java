package br.com.saasfinder.shared.web;

import br.com.saasfinder.shared.error.InvalidParameterException;

/**
 * Paginacao por offset (spec secao 5.1): `page` a partir de 0, `size` ate 50.
 *
 * @param page pagina pedida
 * @param size itens por pagina
 */
public record PagingParams(int page, int size) {

    public static final int SIZE_PADRAO = 20;
    public static final int SIZE_MAXIMO = 50;

    /** Valida na borda: fora da faixa vira 400, nunca uma consulta gigante no banco (AC-15). */
    public static PagingParams de(Integer page, Integer size) {
        int pagina = page == null ? 0 : page;
        int tamanho = size == null ? SIZE_PADRAO : size;

        if (pagina < 0) {
            throw new InvalidParameterException("problema.invalid-parameter.detalhe", "page");
        }
        if (tamanho < 1 || tamanho > SIZE_MAXIMO) {
            throw new InvalidParameterException("problema.invalid-parameter.detalhe", "size");
        }
        return new PagingParams(pagina, tamanho);
    }

    public int offset() {
        return page * size;
    }
}
