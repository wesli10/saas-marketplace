import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ListaComFiltros } from "@/src/components/catalog/ListaComFiltros";
import { SeletorIdioma } from "@/src/components/catalog/SeletorIdioma";
import { JsonLd } from "@/src/components/seo/JsonLd";
import { categoriaPorSlug, listarIntegracoes, listarProdutos } from "@/src/lib/api/catalog";
import { tratarErroDaApi } from "@/src/lib/api/erros";
import { lerFiltro, paraQueryDaApi, paraQueryDaUrl } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { SIZE_PADRAO } from "@/src/lib/paginacao";
import { rotaCategoria } from "@/src/lib/routes";
import { trilhaDaCategoria } from "@/src/lib/seo/jsonld";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * Lista de uma categoria, com os filtros.
 *
 * O estado vive na URL (spec secao 9): a pagina le `searchParams`, monta a
 * query da API e renderiza. Nao ha estado de cliente nenhum, e por isso
 * recarregar reproduz a lista e o botao "voltar" devolve o que era.
 */
export function paginaDeCategoria(idiomaDaRota: AppLocale) {
  return async function Pagina({
    params,
    searchParams,
  }: {
    params: Promise<{ locale: string; slug: string }>;
    searchParams: Promise<Record<string, string | string[] | undefined>>;
  }) {
    const { locale, slug } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    const filtro = lerFiltro(await searchParams);

    // O tamanho da pagina e decisao da tela, mas a URL manda: `?size=` continua
    // valendo, e chega cru na API - inclusive quando for invalido, para o 400
    // vir de la (plano DT-05).
    const query = paraQueryDaApi({ ...filtro, size: filtro.size ?? String(SIZE_PADRAO) });

    const [categoria, lista, integracoes] = await Promise.all([
      categoriaPorSlug(slug, idiomaDaRota).catch(tratarErroDaApi),
      listarProdutos({ ...query, category: slug }, idiomaDaRota).catch(tratarErroDaApi),
      listarIntegracoes(idiomaDaRota).catch(tratarErroDaApi),
    ]);

    return (
      <main className="mx-auto max-w-6xl px-4 py-12">
        <div className="mb-6 flex justify-end">
          {/* O slug da categoria muda com o idioma, e quem tem o par e a API:
              `alternates`, do T07. Adivinhar daria 404 silencioso. */}
          <SeletorIdioma
            locale={idiomaDaRota}
            destino={{
              pagina: "categoria",
              alternates: categoria.alternates,
              query: paraQueryDaUrl(filtro),
            }}
          />
        </div>

        {/* Trilha em JSON-LD: home > categoria. O visitante ve a navegacao na
            tela; o rastreador precisa dela em dado estruturado. */}
        <JsonLd
          dados={trilhaDaCategoria(categoria, idiomaDaRota, textos(idiomaDaRota).site.nome)}
        />

        <h1 data-testid={TESTID.listaTitulo} className="text-3xl font-semibold tracking-tight">
          {categoria.name}
        </h1>
        {categoria.description ? (
          <p className="mt-2 max-w-2xl text-neutral-600">{categoria.description}</p>
        ) : null}

        <ListaComFiltros
          locale={idiomaDaRota}
          base={rotaCategoria(idiomaDaRota, slug)}
          filtro={filtro}
          lista={lista}
          integracoes={integracoes}
        />
      </main>
    );
  };
}

/**
 * O `hreflang` da categoria depende do `alternates` da API: o slug muda com o
 * idioma, e adivinhar publicaria um par reciproco apontando para 404.
 *
 * Quando o slug nao existe, a pagina responde 404 e o metadata nao importa -
 * mas ele nao pode explodir antes disso, entao o erro vira metadata minima.
 */
export function metadataDeCategoria(idiomaDaRota: AppLocale) {
  return async function generateMetadata({
    params,
  }: {
    params: Promise<{ locale: string; slug: string }>;
  }): Promise<Metadata> {
    const { slug } = await params;
    const t = textos(idiomaDaRota);

    const categoria = await categoriaPorSlug(slug, idiomaDaRota).catch(() => null);
    if (!categoria) return { title: t.erro.naoEncontradoTitulo };

    const par = categoria.alternates ?? {};
    return metadataBilingue({
      locale: idiomaDaRota,
      caminhos: {
        "pt-BR": rotaCategoria("pt-BR", par["pt-BR"] ?? slug),
        en: rotaCategoria("en", par["en"] ?? slug),
      },
      titulo: categoria.name ?? "",
      descricao: categoria.description ?? undefined,
    });
  };
}
