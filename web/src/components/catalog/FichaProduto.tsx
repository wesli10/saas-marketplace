import Link from "next/link";

import type { ProdutoDetalhe } from "@/src/lib/api/catalog";
import {
  dataFormatada,
  inicialDe,
  nomeDoIdioma,
  nomeDoPais,
  precoFormatado,
} from "@/src/lib/format";
import { textos } from "@/src/lib/i18n";
import { outroLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaCategoria, rotaProduto } from "@/src/lib/routes";
import { SeletorIdioma } from "@/src/components/catalog/SeletorIdioma";
import { TESTID } from "@/src/lib/testid";

/**
 * A ficha do produto (spec secao 5.3).
 *
 * E componente, e nao markup dentro da pagina, porque o AC-14 so pode ser
 * provado assim: produto publicado sem traducao **nao existe** no seed - o
 * `ProductPublicationValidator` do 001 impede -, e criar um exigiria escrita
 * via admin, que a suite e2e nao faz (plano DT-10.4). Entao o estado chega por
 * props, num teste de componente.
 */
export function FichaProduto({ produto, locale }: { produto: ProdutoDetalhe; locale: AppLocale }) {
  const t = textos(locale);

  const preco = precoFormatado(locale, produto.price_min, produto.price_currency);
  const periodo =
    produto.price_period === "YEAR"
      ? t.produto.porAno
      : produto.price_period === "ONE_TIME"
        ? t.produto.pagamentoUnico
        : t.produto.porMes;

  const pros = produto.pros ?? [];
  const cons = produto.cons ?? [];
  const faq = produto.faq ?? [];

  // Conteudo curado ausente neste idioma (spec 001 secao 5.1): a API responde
  // 200 com os campos longos vazios, e a tela mostra o que existe em vez de
  // quebrar. A `description` fica de fora da conta de proposito - ela vem da
  // ficha basica, e nao da curadoria.
  const semCuradoria =
    pros.length === 0 && cons.length === 0 && faq.length === 0 && !produto.best_for;

  const atualizado = dataFormatada(locale, produto.updated_at);

  return (
    <article className="mx-auto max-w-3xl px-4 py-12">
      <div className="mb-6 flex justify-end">
        {/* Slug de produto e o mesmo nos dois idiomas (spec 001 secao 4.2):
            trocar de idioma aqui leva ao MESMO produto, e nao a home (AC-05). */}
        <SeletorIdioma locale={locale} destino={{ pagina: "produto", slug: produto.slug ?? "" }} />
      </div>

      <header className="flex gap-4">
        <span
          data-testid={TESTID.produtoLogo}
          aria-hidden="true"
          className="flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-2xl font-semibold text-neutral-500"
        >
          {inicialDe(produto.name ?? "")}
        </span>

        <div className="min-w-0">
          <h1 data-testid={TESTID.fichaTitulo} className="text-3xl font-semibold tracking-tight">
            {produto.name}
          </h1>
          <p data-testid={TESTID.fichaTagline} className="mt-1 text-neutral-600">
            {produto.tagline}
          </p>
          {produto.category?.slug ? (
            <Link
              href={rotaCategoria(locale, produto.category.slug)}
              data-testid={TESTID.fichaCategoria}
              data-slug={produto.category.slug}
              className="mt-2 inline-block text-sm underline"
            >
              {produto.category.name}
            </Link>
          ) : null}
        </div>
      </header>

      {semCuradoria ? (
        <p
          role="status"
          data-testid={TESTID.fichaTraducaoAusente}
          className="mt-6 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        >
          {t.ficha.traducaoAusente}{" "}
          <Link
            href={rotaProduto(outroLocale(locale), produto.slug ?? "")}
            hrefLang={outroLocale(locale)}
            data-testid={TESTID.fichaOutraVersao}
            className="underline"
          >
            {t.ficha.verNoOutroIdioma}
          </Link>
        </p>
      ) : null}

      <section className="mt-8 flex flex-wrap items-center gap-4">
        <p data-testid={TESTID.fichaPreco} className="text-xl">
          {preco ? t.produto.aPartirDe(preco, periodo) : t.produto.semPreco}
        </p>
        {produto.pricing_model ? (
          <span data-testid={TESTID.fichaCobranca} className="text-sm text-neutral-600">
            {t.filtro.cobrancaRotulo[produto.pricing_model]}
          </span>
        ) : null}
        {produto.has_free_plan ? <Badge>{t.produto.planoGratuito}</Badge> : null}
        {produto.has_free_trial ? <Badge>{t.produto.testeGratis}</Badge> : null}
      </section>

      {produto.website_url ? (
        <p className="mt-6">
          <a
            href={produto.website_url}
            target="_blank"
            // `nofollow sponsored` porque o catalogo e comercial e o link nao e
            // recomendacao editorial; `noopener` porque `target="_blank"` sem
            // ele entrega `window.opener` ao site de destino (spec secao 5.3).
            rel="nofollow sponsored noopener"
            data-testid={TESTID.fichaSite}
            className="inline-block rounded-md bg-neutral-900 px-4 py-2 font-medium text-white"
          >
            {t.ficha.visitarSite}
          </a>
        </p>
      ) : null}

      {produto.description ? (
        <section className="mt-10">
          <h2 className="text-xl font-medium">{t.ficha.sobre}</h2>
          <p
            data-testid={TESTID.fichaDescricao}
            className="mt-2 whitespace-pre-line text-neutral-700"
          >
            {produto.description}
          </p>
        </section>
      ) : null}

      {(pros.length > 0 || cons.length > 0) && (
        <div className="mt-10 grid gap-8 sm:grid-cols-2">
          {pros.length > 0 && (
            <section>
              <h2 className="text-xl font-medium">{t.ficha.pontosFortes}</h2>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-neutral-700">
                {pros.map((item) => (
                  <li key={item} data-testid={TESTID.fichaPro}>
                    {item}
                  </li>
                ))}
              </ul>
            </section>
          )}
          {cons.length > 0 && (
            <section>
              <h2 className="text-xl font-medium">{t.ficha.pontosFracos}</h2>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-neutral-700">
                {cons.map((item) => (
                  <li key={item} data-testid={TESTID.fichaCon}>
                    {item}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}

      {produto.best_for ? (
        <section className="mt-10">
          <h2 className="text-xl font-medium">{t.ficha.melhorPara}</h2>
          <p data-testid={TESTID.fichaMelhorPara} className="mt-2 text-neutral-700">
            {produto.best_for}
          </p>
        </section>
      ) : null}

      {faq.length > 0 && (
        <section className="mt-10">
          <h2 className="text-xl font-medium">{t.ficha.perguntas}</h2>
          <dl className="mt-2 space-y-4">
            {faq.map((item) => (
              <div key={item.question} data-testid={TESTID.fichaFaqItem}>
                <dt className="font-medium">{item.question}</dt>
                <dd className="mt-1 text-neutral-700">{item.answer}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      <section className="mt-10 grid gap-4 border-t border-neutral-200 pt-6 text-sm sm:grid-cols-2">
        {(produto.supported_languages ?? []).length > 0 && (
          <p data-testid={TESTID.fichaIdiomas}>
            <span className="text-neutral-500">{t.ficha.idiomas}: </span>
            {(produto.supported_languages ?? [])
              .map((codigo) => nomeDoIdioma(locale, codigo))
              .join(", ")}
          </p>
        )}
        {produto.hq_country ? (
          <p data-testid={TESTID.fichaPais}>
            <span className="text-neutral-500">{t.ficha.sede}: </span>
            {nomeDoPais(locale, produto.hq_country)}
          </p>
        ) : null}
        {(produto.integrations ?? []).length > 0 && (
          <p data-testid={TESTID.fichaIntegracoes}>
            <span className="text-neutral-500">{t.ficha.integracoes}: </span>
            {(produto.integrations ?? []).map((i) => i.name).join(", ")}
          </p>
        )}
        {(produto.tags ?? []).length > 0 && (
          <p data-testid={TESTID.fichaTags}>
            <span className="text-neutral-500">{t.ficha.tags}: </span>
            {(produto.tags ?? []).map((tag) => tag.name).join(", ")}
          </p>
        )}
      </section>

      {atualizado ? (
        // Transparencia e posicionamento do produto (spec secao 5.3): quem le
        // precisa saber de quando e o preco que esta vendo.
        <p data-testid={TESTID.fichaAtualizado} className="mt-6 text-sm text-neutral-500">
          {t.ficha.atualizadoEm(atualizado)}
        </p>
      ) : null}
    </article>
  );
}

function Badge({ children }: { children: React.ReactNode }) {
  return (
    <span
      data-testid={TESTID.produtoBadge}
      className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-700"
    >
      {children}
    </span>
  );
}
