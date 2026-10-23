package br.com.saasfinder.search.domain;

/**
 * Unica porta de entrada do modulo `search` (constituicao secao 4).
 *
 * <p>O modulo `catalog` conversa com a busca por aqui e nunca com o repositorio
 * dela.
 */
public interface SearchFacade {

    SearchResult buscar(ProductSearchQuery consulta);
}
