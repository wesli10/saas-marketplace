import type { MetadataRoute } from "next";

import { arvoreDeCategorias, categoriaPorSlug, listarProdutos } from "@/src/lib/api/catalog";
import { LOCALES, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaCategoria, rotaHome, rotaPoliticaDeReviews, rotaProduto } from "@/src/lib/routes";
import { urlAbsoluta } from "@/src/lib/seo/urls";

/**
 * O sitemap sai da API, e nao de uma lista escrita a mao (AC-21).
 *
 * **`force-dynamic` nao e detalhe:** sem isto o Next tentaria pre-renderizar
 * esta rota durante o `npm run build`, e no CI o build roda ANTES de o compose
 * subir - o sitemap sairia vazio, ou o build quebraria, por um motivo que nada
 * na tela denunciaria.
 *
 * Cada pagina entra **duas vezes**, uma por idioma, e cada entrada declara as
 * duas versoes em `alternates.languages`. E redundante de proposito: a entrada
 * separada garante que as duas URLs sejam descobertas, e o `alternates` diz que
 * sao a mesma coisa em idiomas diferentes.
 *
 * A comparacao fica de fora: e `noindex` (T25), e sitemap e onde se pede
 * indexacao. A busca tambem - a rota sem query nao tem conteudo proprio.
 */
export const dynamic = "force-dynamic";

/** O maximo que a API aceita por pagina (`PagingParams.SIZE_MAXIMO`). */
const SIZE_MAXIMO = 50;

type Entrada = MetadataRoute.Sitemap[number];

function porIdioma(
  caminhoDe: (locale: AppLocale) => string,
  extras: Partial<Entrada> = {},
): Entrada[] {
  const languages: Record<string, string> = {};
  for (const idioma of LOCALES) languages[idioma] = urlAbsoluta(caminhoDe(idioma));

  return LOCALES.map((idioma) => ({
    url: urlAbsoluta(caminhoDe(idioma)),
    alternates: { languages },
    ...extras,
  }));
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const entradas: Entrada[] = [
    ...porIdioma((locale) => rotaHome(locale), { priority: 1 }),
    // A politica de reviews entra porque e conteudo publico e estavel, e porque
    // e ela que da lastro ao selo de verificado (spec 003, AC-21a). O formulario
    // e a confirmacao ficam de fora: sao transacionais, e a confirmacao ainda
    // carrega token na URL.
    ...porIdioma((locale) => rotaPoliticaDeReviews(locale), { priority: 0.3 }),
  ];

  // Categoria: o slug muda com o idioma, e quem tem o par e a API. Sao poucas
  // categorias (o 001 tem 5), entao uma ficha por categoria e barato - e o
  // preco de nao adivinhar slug.
  const arvore = await arvoreDeCategorias("pt-BR");
  const raizes = arvore.items ?? [];
  const slugsPt = raizes.flatMap((raiz) => [
    raiz.slug,
    ...(raiz.children ?? []).map((f) => f.slug),
  ]);

  for (const slug of slugsPt) {
    if (!slug) continue;
    const categoria = await categoriaPorSlug(slug, "pt-BR");
    const par = categoria.alternates ?? {};
    entradas.push(
      ...porIdioma((locale) => rotaCategoria(locale, par[locale] ?? slug), { priority: 0.8 }),
    );
  }

  // Produto: o slug e o mesmo nos dois idiomas (spec 001 secao 4.2).
  let pagina = 0;
  for (;;) {
    const lista = await listarProdutos({ page: pagina, size: SIZE_MAXIMO }, "pt-BR");
    const itens = lista.items ?? [];
    if (itens.length === 0) break;

    for (const produto of itens) {
      if (!produto.slug) continue;
      entradas.push(
        ...porIdioma((locale) => rotaProduto(locale, produto.slug ?? ""), { priority: 0.6 }),
      );
    }

    pagina += 1;
    if (pagina * SIZE_MAXIMO >= (lista.total ?? 0)) break;
  }

  return entradas;
}
