import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BuscaRapida } from "@/src/components/catalog/BuscaRapida";
import { ListaComFiltros } from "@/src/components/catalog/ListaComFiltros";
import { SeletorIdioma } from "@/src/components/catalog/SeletorIdioma";
import { listarIntegracoes, listarProdutos } from "@/src/lib/api/catalog";
import { tratarErroDaApi } from "@/src/lib/api/erros";
import { lerFiltro, paraQueryDaApi, paraQueryDaUrl } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { SIZE_PADRAO } from "@/src/lib/paginacao";
import { rotaBusca } from "@/src/lib/routes";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * Busca: a mesma lista da categoria, por outra porta (spec secao 5.2).
 *
 * A tela nao decide nada sobre relevancia - quem casa, ordena e diz por qual
 * caminho casou e a API, no `query.matched_by`. Aqui isso vira um aviso: quando
 * o resultado veio do fallback de trigram, quem le precisa saber que esta vendo
 * o que mais se aproxima, e nao o que pediu (AC-11). Sem o aviso, uma lista
 * aproximada e indistinguivel de uma lista exata - e a transparencia e
 * posicionamento do produto, nao enfeite.
 */
export function paginaDeBusca(idiomaDaRota: AppLocale) {
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
    const filtro = lerFiltro(await searchParams);
    const termo = filtro.q ?? "";

    const query = paraQueryDaApi({ ...filtro, size: filtro.size ?? String(SIZE_PADRAO) });

    // Busca sem termo nao vai para a API: `GET /products` sem `q` devolveria o
    // catalogo inteiro, e uma lista de tudo com o titulo "resultados para" e
    // uma resposta errada com cara de certa.
    const [lista, integracoes] = termo
      ? await Promise.all([
          listarProdutos(query, idiomaDaRota).catch(tratarErroDaApi),
          listarIntegracoes(idiomaDaRota).catch(tratarErroDaApi),
        ])
      : [null, null];

    const aproximado = lista?.query?.matched_by === "TRIGRAM";

    return (
      <main className="mx-auto max-w-6xl px-4 py-12">
        <div className="mb-6 flex justify-end">
          {/* A busca vai junto: trocar de idioma no meio de uma busca e
              continuar a mesma busca, e nao recomecar. */}
          <SeletorIdioma
            locale={idiomaDaRota}
            destino={{ pagina: "busca", query: paraQueryDaUrl(filtro) }}
          />
        </div>

        <h1 data-testid={TESTID.listaTitulo} className="text-3xl font-semibold tracking-tight">
          {termo ? t.busca.resultadosPara(termo) : t.busca.rotulo}
        </h1>

        <div className="mt-6">
          {/* O campo vem preenchido com o termo: refinar a busca e mudar uma
              palavra, e nao digitar tudo de novo. */}
          <BuscaRapida locale={idiomaDaRota} termo={termo} />
        </div>

        {aproximado ? (
          <p
            role="status"
            data-testid={TESTID.buscaAproximada}
            className="mt-6 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
          >
            {t.busca.aproximado(termo)}
          </p>
        ) : null}

        {lista && integracoes ? (
          <ListaComFiltros
            locale={idiomaDaRota}
            base={rotaBusca(idiomaDaRota)}
            filtro={filtro}
            lista={lista}
            integracoes={integracoes}
          />
        ) : (
          <p className="mt-8 text-neutral-600" data-testid={TESTID.buscaSemTermo}>
            {t.busca.semTermo}
          </p>
        )}
      </main>
    );
  };
}

/**
 * A canonica da busca e a rota **sem** a query: cada termo digitado geraria uma
 * URL nova sem conteudo proprio, e o indice encheria de variacoes da mesma
 * pagina. O par de `hreflang` segue existindo, porque a rota existe nos dois
 * idiomas (AC-06).
 */
export function metadataDeBusca(locale: AppLocale): Metadata {
  const t = textos(locale);
  return metadataBilingue({
    locale,
    caminhos: { "pt-BR": rotaBusca("pt-BR"), en: rotaBusca("en") },
    titulo: t.busca.rotulo,
    descricao: t.site.descricao,
  });
}
