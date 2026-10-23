import type { Metadata } from "next";
import { notFound } from "next/navigation";
import Link from "next/link";

import { SeletorIdioma } from "@/src/components/catalog/SeletorIdioma";
import { TabelaComparacao } from "@/src/components/catalog/TabelaComparacao";
import { compararProdutos } from "@/src/lib/api/catalog";
import { ApiError } from "@/src/lib/api/client";
import {
  faixaValida,
  lerSlugs,
  MAXIMO_PARA_COMPARAR,
  MINIMO_PARA_COMPARAR,
} from "@/src/lib/comparacao";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaComparacao, rotaHome } from "@/src/lib/routes";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * Comparacao (spec secao 5.4).
 *
 * Os dois casos de erro sao tratados na tela, e nao entregues como erro:
 *
 *  - **faixa invalida** (menos de 2 ou mais de 3): a API responderia 400, e um
 *    400 aqui seria a pagina de erro para quem so clicou em "comparar" com um
 *    produto so. A tela pede para escolher de 2 a 3, com status 200 (AC-16).
 *  - **slug inexistente**: a API responde 404 com `missing_slugs`, e a tela diz
 *    quais sumiram e oferece comparar os que restaram (AC-17). Nada de 404 na
 *    cara de quem tinha dois slugs bons e um velho.
 *
 * O `detail` do Problem Details **nunca** vai para a tela: ele vem no idioma da
 * API e apareceria cru numa pagina em ingles (AC-19). O que se usa e o campo
 * estruturado `missing_slugs`.
 */
export function paginaDeComparacao(idiomaDaRota: AppLocale) {
  return async function Pagina({
    params,
    searchParams,
  }: {
    params: Promise<{ locale: string }>;
    searchParams: Promise<Record<string, string | string[] | undefined>>;
  }) {
    const { locale } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    const t = textos(idiomaDaRota);
    const pedidos = lerSlugs((await searchParams).slugs);

    const moldura = (conteudo: React.ReactNode) => (
      <main className="mx-auto max-w-6xl px-4 py-12">
        <div className="mb-6 flex justify-end">
          <SeletorIdioma locale={idiomaDaRota} destino={{ pagina: "home" }} />
        </div>
        <h1 data-testid={TESTID.listaTitulo} className="text-3xl font-semibold tracking-tight">
          {t.comparacao.titulo}
        </h1>
        {conteudo}
      </main>
    );

    if (!faixaValida(pedidos)) {
      return moldura(
        <div className="mt-6">
          <p data-testid={TESTID.comparacaoFaixa}>
            {t.comparacao.faixa(MINIMO_PARA_COMPARAR, MAXIMO_PARA_COMPARAR)}
          </p>
          <Link
            href={rotaHome(idiomaDaRota)}
            data-testid={TESTID.linkHome}
            className="mt-4 inline-block underline"
          >
            {t.navegacao.irParaHome}
          </Link>
        </div>,
      );
    }

    try {
      const comparacao = await compararProdutos(pedidos, idiomaDaRota);
      return moldura(
        <TabelaComparacao
          produtos={comparacao.items ?? []}
          locale={idiomaDaRota}
          ordem={pedidos}
        />,
      );
    } catch (erro) {
      const ausentes = erro instanceof ApiError ? (erro.problema?.missing_slugs ?? []) : [];
      if (ausentes.length === 0) throw erro;

      const restantes = pedidos.filter((slug) => !ausentes.includes(slug));

      return moldura(
        <div className="mt-6">
          <p data-testid={TESTID.comparacaoAusentes}>
            {t.comparacao.ausentes(ausentes.join(", "))}
          </p>

          {faixaValida(restantes) ? (
            <Link
              href={rotaComparacao(idiomaDaRota, restantes)}
              data-testid={TESTID.comparacaoSeguir}
              className="mt-4 inline-block underline"
            >
              {t.comparacao.seguirComOsQueRestaram}
            </Link>
          ) : (
            <p className="mt-4" data-testid={TESTID.comparacaoFaixa}>
              {t.comparacao.faixa(MINIMO_PARA_COMPARAR, MAXIMO_PARA_COMPARAR)}
            </p>
          )}
        </div>,
      );
    }
  };
}

/**
 * A unica pagina fora do indice: ela e uma consulta montada pelo visitante, e
 * cada combinacao de slugs seria uma URL sem conteudo proprio. `follow` fica
 * ligado - os links para as fichas continuam valendo.
 */
export function metadataDaComparacao(locale: AppLocale): Metadata {
  const t = textos(locale);
  return metadataBilingue({
    locale,
    caminhos: { "pt-BR": rotaComparacao("pt-BR", []), en: rotaComparacao("en", []) },
    titulo: t.comparacao.titulo,
    indexavel: false,
  });
}
