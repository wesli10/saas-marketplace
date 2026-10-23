import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { FormularioDeReview } from "@/src/components/reviews/FormularioDeReview";
import { produtoPorSlug } from "@/src/lib/api/catalog";
import { tratarErroDaApi } from "@/src/lib/api/erros";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaAvaliar, rotaProduto } from "@/src/lib/routes";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * Escrever uma avaliacao (spec 003 secao 6).
 *
 * Pagina propria, e nao um formulario dentro da ficha: sem JavaScript, um
 * formulario embutido faria a ficha inteira recarregar a cada erro de
 * validacao, e o visitante que so queria ler perderia o lugar. Como rota, ela
 * tem endereco proprio, botao "voltar" que funciona e um estado de sucesso que
 * pode ser recarregado sem reenviar nada.
 */
export function paginaDeAvaliar(idiomaDaRota: AppLocale) {
  return async function Pagina({
    params,
    searchParams,
  }: {
    params: Promise<{ locale: string; slug: string }>;
    searchParams: Promise<Record<string, string | string[] | undefined>>;
  }) {
    const { locale, slug } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    // Slug inexistente vira 404 de verdade, como na ficha: formulario para um
    // produto que nao existe nao deveria abrir.
    const produto = await produtoPorSlug(slug, idiomaDaRota).catch(tratarErroDaApi);
    const t = textos(idiomaDaRota);
    const enviado = (await searchParams).enviado === "1";

    if (enviado) {
      return (
        <main className="mx-auto max-w-2xl px-4 py-12">
          <section data-testid={TESTID.avaliarEmAnalise} role="status">
            <h1 className="text-3xl font-semibold tracking-tight">{t.avaliar.emAnaliseTitulo}</h1>
            {/* Com todas as letras: quem escreveu precisa entender que a
                avaliacao esta em analise, e nao que ela sumiu (AC-18). */}
            <p className="mt-4 text-neutral-700">{t.avaliar.emAnalise}</p>
            <p className="mt-2 text-sm text-neutral-500">{t.avaliar.emAnaliseSpam}</p>
          </section>

          <p className="mt-8">
            <Link
              href={rotaProduto(idiomaDaRota, slug)}
              data-testid={TESTID.avaliarVoltar}
              className="underline"
            >
              {t.avaliar.voltarParaFicha}
            </Link>
          </p>
        </main>
      );
    }

    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <h1 className="text-3xl font-semibold tracking-tight">
          {t.avaliar.titulo(produto.name ?? slug)}
        </h1>
        <p className="mt-4 text-neutral-700">{t.avaliar.comoFunciona}</p>

        <FormularioDeReview slug={slug} locale={idiomaDaRota} />
      </main>
    );
  };
}

export function metadataDeAvaliar(idiomaDaRota: AppLocale) {
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
      caminhos: { "pt-BR": rotaAvaliar("pt-BR", slug), en: rotaAvaliar("en", slug) },
      titulo: t.avaliar.titulo(produto.name ?? slug),
      descricao: t.avaliar.comoFunciona,
    });
  };
}
