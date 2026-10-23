import type { Metadata } from "next";

import { LOCALE_PADRAO, LOCALES, type AppLocale } from "@/src/lib/i18n/locales";

import { urlAbsoluta, urlDoSite } from "./urls";

/** O caminho da mesma pagina em cada idioma. */
export type CaminhosPorIdioma = Record<AppLocale, string>;

/**
 * `title`, `description`, canonica absoluta e `hreflang` reciproco (AC-06).
 *
 * O `hreflang` sai de `alternates.languages`, e **as duas versoes sempre
 * aparecem nas duas paginas** - reciprocidade e o que o rastreador exige para
 * tratar o par como o mesmo conteudo em dois idiomas; uma seta so ele ignora.
 *
 * `x-default` aponta para pt-BR (spec secao 4): e o idioma padrao da aplicacao,
 * o mesmo destino do redirect da raiz quando o `Accept-Language` nao ajuda.
 */
export function metadataBilingue({
  locale,
  caminhos,
  titulo,
  descricao,
  indexavel = true,
}: {
  locale: AppLocale;
  caminhos: CaminhosPorIdioma;
  titulo: string;
  descricao?: string;
  indexavel?: boolean;
}): Metadata {
  const languages: Record<string, string> = {};
  for (const idioma of LOCALES) languages[idioma] = urlAbsoluta(caminhos[idioma]);
  languages["x-default"] = urlAbsoluta(caminhos[LOCALE_PADRAO]);

  return {
    metadataBase: new URL(urlDoSite()),
    title: titulo,
    description: descricao,
    alternates: { canonical: urlAbsoluta(caminhos[locale]), languages },
    // A comparacao e a unica pagina que nao entra no indice: ela e uma consulta
    // montada pelo visitante, e cada combinacao de slugs seria uma URL nova sem
    // conteudo proprio.
    robots: indexavel ? undefined : { index: false, follow: true },
    openGraph: {
      title: titulo,
      description: descricao,
      url: urlAbsoluta(caminhos[locale]),
      locale,
      type: "website",
    },
  };
}
