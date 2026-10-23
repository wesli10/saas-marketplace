import type { AppLocale } from "./i18n/locales";

/**
 * O unico lugar que sabe montar uma URL desta aplicacao.
 *
 * Os segmentos sao traduzidos (`categorias`/`categories`), entao link montado
 * por template string funciona num idioma e quebra no outro - e quebra em
 * silencio, virando 404 so para quem le em ingles. Toda geracao de href passa
 * por aqui; a varredura do CI confere que nao ha atalho.
 *
 * Slug de produto NAO muda com o idioma (spec 001 secao 4.2); slug de categoria
 * muda, e quem tem o par e a API, no campo `alternates`.
 */
const SEGMENTOS = {
  categoria: { "pt-BR": "categorias", en: "categories" },
  produto: { "pt-BR": "produtos", en: "products" },
  busca: { "pt-BR": "buscar", en: "search" },
  comparacao: { "pt-BR": "comparar", en: "compare" },
  politicaDeReviews: { "pt-BR": "politica-de-reviews", en: "review-policy" },
} as const;

/** O segmento final da rota de avaliar, dentro da rota do produto. */
const AVALIAR = { "pt-BR": "avaliar", en: "review" } as const;

export type Pagina = "home" | keyof typeof SEGMENTOS;

/** O segmento traduzido de uma pagina, ou `null` para a home. */
export function segmentoDe(pagina: Pagina, locale: AppLocale): string | null {
  return pagina === "home" ? null : SEGMENTOS[pagina][locale];
}

export function rotaHome(locale: AppLocale): string {
  return `/${locale}`;
}

export function rotaCategoria(locale: AppLocale, slug: string): string {
  return `/${locale}/${SEGMENTOS.categoria[locale]}/${slug}`;
}

export function rotaProduto(locale: AppLocale, slug: string): string {
  return `/${locale}/${SEGMENTOS.produto[locale]}/${slug}`;
}

export function rotaBusca(locale: AppLocale, query?: URLSearchParams | string): string {
  return comQuery(`/${locale}/${SEGMENTOS.busca[locale]}`, query);
}

export function rotaComparacao(locale: AppLocale, slugs: readonly string[]): string {
  const base = `/${locale}/${SEGMENTOS.comparacao[locale]}`;
  return slugs.length === 0 ? base : `${base}?slugs=${slugs.join(",")}`;
}

/** Cola uma query numa rota ja montada, sem deixar `?` sobrando quando vazia. */
/**
 * A politica de reviews (spec 003 secao 6).
 *
 * E ela que da lastro ao selo de verificado: sem uma pagina publica dizendo
 * quem pode avaliar, o que e verificado e o que e removido, o selo e uma
 * afirmacao sem lastro.
 */
export function rotaPoliticaDeReviews(locale: AppLocale): string {
  return `/${locale}/${SEGMENTOS.politicaDeReviews[locale]}`;
}

/**
 * O formulario de avaliacao de um produto (spec 003 secao 6).
 *
 * Pendurado na rota do produto, e nao numa rota solta: a avaliacao e sempre de
 * um produto, e o endereco diz qual sem depender de query string.
 */
export function rotaAvaliar(locale: AppLocale, slug: string): string {
  return `${rotaProduto(locale, slug)}/${AVALIAR[locale]}`;
}

export function comQuery(base: string, query?: URLSearchParams | string): string {
  if (query === undefined) return base;
  const texto = typeof query === "string" ? query : query.toString();
  return texto === "" ? base : `${base}?${texto}`;
}
