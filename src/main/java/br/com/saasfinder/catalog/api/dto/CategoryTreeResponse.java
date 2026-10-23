package br.com.saasfinder.catalog.api.dto;

import br.com.saasfinder.catalog.domain.CategoryNodeView;
import java.util.List;

/**
 * Envelope da arvore de categorias.
 *
 * <p>O {@code locale} vem no corpo, e nao so no cabecalho Content-Language,
 * porque o cliente costuma guardar a resposta e depois precisa saber em que
 * idioma ela foi montada.
 */
public record CategoryTreeResponse(String locale, List<CategoryNodeView> items) {}
