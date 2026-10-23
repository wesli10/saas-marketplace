import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { FichaProduto } from "@/src/components/catalog/FichaProduto";
import { BlocoDeReviews } from "@/src/components/reviews/BlocoDeReviews";
import { PARAM_DE_PAGINA } from "@/src/components/reviews/ListaDeReviews";
import { JsonLd } from "@/src/components/seo/JsonLd";
import { produtoPorSlug } from "@/src/lib/api/catalog";
import { reviewsDoProduto, type ListaDeReviews } from "@/src/lib/api/reviews";
import { tratarErroDaApi } from "@/src/lib/api/erros";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaProduto } from "@/src/lib/routes";
import { softwareApplication, trilhaDoProduto } from "@/src/lib/seo/jsonld";
import { metadataBilingue } from "@/src/lib/seo/metadata";

/**
 * Ficha de produto. A pagina resolve o slug e trata o erro; o que desenha e o
 * componente, que recebe tudo por props e por isso pode ser testado com um
 * produto que o seed nao tem (AC-14).
 *
 * Slug inexistente vira 404 de verdade - status HTTP, e nao pagina vazia com
 * 200 (AC-13). Quem faz isso e o `tratarErroDaApi`, no servidor.
 */
export function paginaDeProduto(idiomaDaRota: AppLocale) {
  return async function Pagina({
    params,
    searchParams,
  }: {
    params: Promise<{ locale: string; slug: string }>;
    searchParams: Promise<Record<string, string | string[] | undefined>>;
  }) {
    const { locale, slug } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    const produto = await produtoPorSlug(slug, idiomaDaRota).catch(tratarErroDaApi);

    // As avaliacoes sao conteudo adicional: falha aqui vira aviso no bloco, e
    // nao pagina de erro. Derrubar a ficha inteira do produto porque um
    // endpoint secundario piscou seria trocar uma perda pequena por uma grande.
    const avaliacoes: ListaDeReviews | null = await reviewsDoProduto(
      slug,
      idiomaDaRota,
      paginaPedida(await searchParams),
    ).catch(() => null);

    return (
      <main>
        {/* SoftwareApplication com nome, categoria e preco quando houver
            (AC-15), mais a trilha home > categoria > produto. */}
        <JsonLd
          dados={[
            softwareApplication(produto, idiomaDaRota),
            trilhaDoProduto(produto, idiomaDaRota, textos(idiomaDaRota).site.nome),
          ]}
        />
        <FichaProduto produto={produto} locale={idiomaDaRota} />
        <BlocoDeReviews
          lista={avaliacoes}
          locale={idiomaDaRota}
          base={rotaProduto(idiomaDaRota, slug)}
          slug={slug}
        />
      </main>
    );
  };
}

/**
 * A pagina das avaliacoes, lida da URL (constituicao secao 4: o estado de
 * navegacao vive la). Valor torto vira a primeira pagina, e nao um 400: quem
 * digitou `?avaliacoes=abc` na barra quer ver a ficha, nao um erro.
 */
function paginaPedida(query: Record<string, string | string[] | undefined>): number {
  const bruto = query[PARAM_DE_PAGINA];
  const numero = Number(Array.isArray(bruto) ? bruto[0] : bruto);
  return Number.isInteger(numero) && numero > 0 ? numero : 0;
}

/**
 * Slug de produto nao muda com o idioma (spec 001 secao 4.2), entao o par de
 * `hreflang` sai do proprio slug com o segmento traduzido - sem chamada extra
 * (plano DT, secao do `alternates`).
 */
export function metadataDeProduto(idiomaDaRota: AppLocale) {
  return async function generateMetadata({
    params,
  }: {
    params: Promise<{ locale: string; slug: string }>;
  }): Promise<Metadata> {
    const { slug } = await params;
    const t = textos(idiomaDaRota);

    const produto = await produtoPorSlug(slug, idiomaDaRota).catch(() => null);
    if (!produto) return { title: t.erro.naoEncontradoTitulo };

    return metadataBilingue({
      locale: idiomaDaRota,
      caminhos: { "pt-BR": rotaProduto("pt-BR", slug), en: rotaProduto("en", slug) },
      titulo: produto.name ?? "",
      descricao: produto.tagline ?? undefined,
    });
  };
}
