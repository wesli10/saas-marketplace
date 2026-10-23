import type { CategoriaDetalhe, ProdutoDetalhe } from "@/src/lib/api/catalog";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaCategoria, rotaHome, rotaProduto } from "@/src/lib/routes";

import { urlAbsoluta } from "./urls";

/**
 * JSON-LD montado por funcao pura (plano DT: `lib/seo/`).
 *
 * Fora do componente porque e **dado**, e nao markup: assim o formato pode ser
 * verificado em teste de unidade (AC-15) sem renderizar pagina, e o componente
 * fica com uma responsabilidade so, que e serializar.
 *
 * Campo que nao existe nao vira campo vazio: `price` ausente e diferente de
 * preco zero, e `offers: { price: "" }` seria uma afirmacao falsa sobre o
 * produto - o tipo de coisa que faz o Google descartar o bloco inteiro.
 */
export interface JsonLd {
  "@context": "https://schema.org";
  "@type": string;
  [chave: string]: unknown;
}

export function softwareApplication(produto: ProdutoDetalhe, locale: AppLocale): JsonLd {
  const dados: JsonLd = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: produto.name ?? "",
    url: urlAbsoluta(rotaProduto(locale, produto.slug ?? "")),
    inLanguage: locale,
  };

  if (produto.tagline) dados.abstract = produto.tagline;
  if (produto.description) dados.description = produto.description;
  if (produto.category?.name) dados.applicationCategory = produto.category.name;
  if (produto.website_url) dados.sameAs = [produto.website_url];

  // `offers` so quando ha preco publicado. O catalogo tem produto "sob
  // consulta", e anunciar preco que nao existe e pior do que nao anunciar.
  if (produto.price_min !== undefined && produto.price_currency) {
    dados.offers = {
      "@type": "Offer",
      price: String(produto.price_min),
      priceCurrency: produto.price_currency,
      url: produto.website_url ?? urlAbsoluta(rotaProduto(locale, produto.slug ?? "")),
    };
  }

  return dados;
}

/**
 * A trilha ate a pagina atual.
 *
 * A posicao comeca em 1, e nao em 0 - `BreadcrumbList` e do schema.org, e la a
 * contagem e humana.
 */
export function breadcrumbList(
  itens: readonly { nome: string; caminho: string }[],
  locale: AppLocale,
): JsonLd {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    inLanguage: locale,
    itemListElement: itens.map((item, indice) => ({
      "@type": "ListItem",
      position: indice + 1,
      name: item.nome,
      item: urlAbsoluta(item.caminho),
    })),
  };
}

/** A trilha da categoria: home > categoria. */
export function trilhaDaCategoria(
  categoria: CategoriaDetalhe,
  locale: AppLocale,
  inicio: string,
): JsonLd {
  return breadcrumbList(
    [
      { nome: inicio, caminho: rotaHome(locale) },
      { nome: categoria.name ?? "", caminho: rotaCategoria(locale, categoria.slug ?? "") },
    ],
    locale,
  );
}

/** A trilha do produto: home > categoria > produto. */
export function trilhaDoProduto(
  produto: ProdutoDetalhe,
  locale: AppLocale,
  inicio: string,
): JsonLd {
  const itens = [{ nome: inicio, caminho: rotaHome(locale) }];

  if (produto.category?.slug) {
    itens.push({
      nome: produto.category.name ?? "",
      caminho: rotaCategoria(locale, produto.category.slug),
    });
  }
  itens.push({ nome: produto.name ?? "", caminho: rotaProduto(locale, produto.slug ?? "") });

  return breadcrumbList(itens, locale);
}
