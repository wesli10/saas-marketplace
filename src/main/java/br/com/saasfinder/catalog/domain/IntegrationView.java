package br.com.saasfinder.catalog.domain;

/**
 * Integracao com quantos produtos publicados a usam (plan.md secao 4.6).
 *
 * <p>Nao tem `locale`: nome de integracao e nome proprio (WhatsApp, HubSpot) e
 * nao se traduz, entao nao ha idioma a resolver aqui.
 *
 * @param productCount so publicados - DRAFT e ARCHIVED nao existem para o publico
 */
public record IntegrationView(String slug, String name, long productCount) {}
