package br.com.saasfinder.catalog.domain;

import java.util.List;

/**
 * Conteudo curado de um produto num idioma (plan.md secao 5.2).
 *
 * <p>A semantica e de **substituicao**: o que chega descreve o registro daquele
 * locale por inteiro, entao item que sumiu do corpo sumiu do produto.
 */
public record DadosDeConteudo(List<String> pros, List<String> cons, String bestFor, List<FaqItem> faq) {}
