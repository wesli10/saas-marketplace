package br.com.saasfinder.catalog.domain;

import java.util.List;

/**
 * Uma pagina da listagem de curadoria.
 *
 * <p>Tipo proprio em vez do `Page` do Spring: a estrutura JSON dele e instavel
 * entre versoes e vaza `pageable`/`sort` (plan.md DT-12).
 */
public record PaginaDeProdutos(List<ProductAdminView> itens, long total) {}
