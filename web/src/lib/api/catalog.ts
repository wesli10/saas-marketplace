import "server-only";

import type { AppLocale } from "@/src/lib/i18n/locales";

import { buscarNaApi } from "./client";
import type { components } from "./types.gen";

/**
 * As paginas chamam estas funcoes, nunca o `client` direto.
 *
 * O motivo e o mesmo de ter um mapa de rotas: quando a API mudar de forma, o
 * lugar a corrigir e um so. Os tipos vem do OpenAPI (types.gen.ts), entao uma
 * mudanca de contrato aparece aqui como erro de compilacao.
 */
type Esquemas = components["schemas"];

export type ArvoreDeCategorias = Esquemas["CategoryTreeResponse"];
export type CategoriaDetalhe = Esquemas["CategoryDetailView"];
export type ListaDeProdutos = Esquemas["ProductListResponse"];
/** O item da lista - o que o card recebe. Vem do contrato, nao escrito a mao. */
export type ProdutoResumo = Esquemas["ProductSummaryView"];
export type ProdutoDetalhe = Esquemas["ProductDetailView"];
export type Comparacao = Esquemas["ProductCompareResponse"];
export type ListaDeIntegracoes = Esquemas["IntegrationListResponse"];

export function arvoreDeCategorias(locale: AppLocale) {
  return buscarNaApi<ArvoreDeCategorias>("/api/v1/categories", { locale });
}

export function categoriaPorSlug(slug: string, locale: AppLocale) {
  return buscarNaApi<CategoriaDetalhe>(`/api/v1/categories/${encodeURIComponent(slug)}`, {
    locale,
  });
}

export interface FiltroDeProdutos {
  q?: string;
  page?: number;
  size?: number;
  category?: string;
  integration?: string[];
  pricing_model?: string[];
  has_free_trial?: boolean;
  has_free_plan?: boolean;
  price_max?: number;
}

export function listarProdutos(filtro: FiltroDeProdutos, locale: AppLocale) {
  return buscarNaApi<ListaDeProdutos>("/api/v1/products", { locale, query: { ...filtro } });
}

export function produtoPorSlug(slug: string, locale: AppLocale) {
  return buscarNaApi<ProdutoDetalhe>(`/api/v1/products/${encodeURIComponent(slug)}`, { locale });
}

export function compararProdutos(slugs: readonly string[], locale: AppLocale) {
  return buscarNaApi<Comparacao>("/api/v1/products/compare", {
    locale,
    query: { slugs: slugs.join(",") },
  });
}

export function listarIntegracoes(locale: AppLocale) {
  return buscarNaApi<ListaDeIntegracoes>("/api/v1/integrations", { locale });
}
