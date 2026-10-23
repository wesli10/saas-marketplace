import type { Metadata } from "next";

import { BuscaRapida } from "@/src/components/catalog/BuscaRapida";
import { CategoriaCard } from "@/src/components/catalog/CategoriaCard";
import { SeletorIdioma } from "@/src/components/catalog/SeletorIdioma";
import { arvoreDeCategorias } from "@/src/lib/api/catalog";
import { textos } from "@/src/lib/i18n";
import { type AppLocale } from "@/src/lib/i18n/locales";
import { rotaHome } from "@/src/lib/routes";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * Home: as categorias raiz com a contagem, as subcategorias como cards, e a
 * busca. Sem carrossel e sem "destaques" - nao existe criterio de destaque
 * nesta fatia, e inventar um violaria a constituicao secao 7 (spec 002 secao
 * 5.1).
 */
export async function Home({ locale }: { locale: AppLocale }) {
  const t = textos(locale);
  const arvore = await arvoreDeCategorias(locale);
  const raizes = arvore.items ?? [];

  return (
    <main className="mx-auto max-w-5xl px-4 py-12">
      <div className="mb-6 flex justify-end">
        <SeletorIdioma locale={locale} destino={{ pagina: "home" }} />
      </div>

      <h1 data-testid={TESTID.homeTitulo} className="text-3xl font-semibold tracking-tight">
        {t.site.nome}
      </h1>
      <p className="mt-2 max-w-2xl text-neutral-600">{t.site.descricao}</p>

      <div className="mt-8">
        <BuscaRapida locale={locale} />
      </div>

      <h2 className="mt-14 text-xl font-medium">{t.categoria.explorar}</h2>

      {raizes.map((raiz) => (
        <section
          key={raiz.slug}
          data-testid={TESTID.categoriaRaiz}
          data-slug={raiz.slug}
          className="mt-6"
        >
          <h3 className="text-sm font-medium tracking-wide text-neutral-500 uppercase">
            {raiz.name} · {t.categoria.produtos(raiz.product_count ?? 0)}
          </h3>

          <ul className="mt-3 grid gap-4 sm:grid-cols-2">
            {(raiz.children ?? []).map((filha) => (
              <CategoriaCard
                key={filha.slug}
                locale={locale}
                categoria={{
                  slug: filha.slug ?? "",
                  name: filha.name ?? "",
                  description: filha.description,
                  productCount: filha.product_count ?? 0,
                }}
              />
            ))}
          </ul>
        </section>
      ))}
    </main>
  );
}

/** Canonica absoluta e o par de `hreflang` (AC-06). */
export function metadataDaHome(locale: AppLocale): Metadata {
  const t = textos(locale);
  return metadataBilingue({
    locale,
    caminhos: { "pt-BR": rotaHome("pt-BR"), en: rotaHome("en") },
    titulo: t.site.nome,
    descricao: t.site.descricao,
  });
}
