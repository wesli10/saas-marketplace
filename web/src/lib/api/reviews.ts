import "server-only";

import type { AppLocale } from "@/src/lib/i18n/locales";

import { buscarNaApi, enviarParaApi } from "./client";
import type { components } from "./types.gen";

/**
 * Leitura publica de reviews (spec 003 secao 5.1).
 *
 * Mesma regra do `catalog.ts`: as paginas chamam daqui, nunca o `client`
 * direto, e os tipos vem do OpenAPI - mudanca de contrato aparece como erro de
 * compilacao, e nao como `undefined` em producao.
 */
type Esquemas = components["schemas"];

export type ListaDeReviews = Esquemas["ReviewListResponse"];
export type Review = Esquemas["ReviewView"];
export type NotaDoProduto = Esquemas["NotaDoProduto"];

/** Quantas reviews por pagina na ficha. */
export const REVIEWS_POR_PAGINA = 5;

/** O corpo que o POST publico espera (spec 003 secao 5.1). */
export type NovaReview = Esquemas["ReviewRequest"];

/**
 * Envia uma avaliacao (AC-01). Responde 202 sempre que a entrada e valida -
 * inclusive quando ja existe review daquele e-mail para aquele produto, e e por
 * isso que esta funcao nao devolve nada: nao ha o que dizer sem revelar o que a
 * resposta uniforme existe para esconder.
 */
export function criarReview(slug: string, locale: AppLocale, corpo: NovaReview) {
  return enviarParaApi(`/api/v1/products/${encodeURIComponent(slug)}/reviews`, { locale, corpo });
}

/**
 * Confirma o e-mail de uma avaliacao (AC-03).
 *
 * Token usado, expirado ou desconhecido respondem os tres 410 - a API nao
 * distingue de proposito, e a tela tambem nao.
 */
export function confirmarReview(token: string, locale: AppLocale) {
  return enviarParaApi("/api/v1/reviews/confirm", { locale, corpo: { token } });
}

/** Pede um link novo. Responde 202 sempre, inclusive quando nao ha o que reenviar. */
export function reenviarConfirmacao(produto: string, email: string, locale: AppLocale) {
  return enviarParaApi("/api/v1/reviews/resend", {
    locale,
    corpo: { product_slug: produto, email },
  });
}

export function reviewsDoProduto(slug: string, locale: AppLocale, pagina = 0) {
  return buscarNaApi<ListaDeReviews>(`/api/v1/products/${encodeURIComponent(slug)}/reviews`, {
    locale,
    query: { page: pagina, size: REVIEWS_POR_PAGINA },
  });
}
