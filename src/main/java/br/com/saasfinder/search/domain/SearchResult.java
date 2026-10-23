package br.com.saasfinder.search.domain;

import java.util.List;

/**
 * Resultado de uma consulta.
 *
 * @param itens pagina pedida
 * @param total quantos existem no total, para o cliente montar a paginacao
 * @param matchedBy como o resultado foi encontrado; nulo quando a consulta nao
 *     tinha texto - sem texto nao houve casamento a relatar
 */
public record SearchResult(List<ProductSummaryView> itens, long total, MatchedBy matchedBy) {}
