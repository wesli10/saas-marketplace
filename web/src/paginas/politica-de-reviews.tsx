import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { CONTATO_DE_CONTESTACAO, PRAZO_DE_RESPOSTA } from "@/src/lib/reviews/contestacao";
import { rotaPoliticaDeReviews } from "@/src/lib/routes";
import { metadataBilingue } from "@/src/lib/seo/metadata";
import { TESTID } from "@/src/lib/testid";

/**
 * A politica de reviews (spec 003 secao 6, exigida pelo plano de produto §9).
 *
 * E o que da lastro ao selo: sem uma pagina publica dizendo quem pode avaliar, o
 * que "verificado" significa e o que e removido, o selo e uma afirmacao sem
 * lastro. E e tambem a mitigacao que cabe numa vitrine (spec secao 7.1): sem
 * `vendor`, a empresa avaliada nao tem onde reclamar dentro do produto, entao o
 * canal de contestacao e um endereco publicado aqui - com prazo.
 *
 * Conteudo, e nao componente: o texto vive no catalogo de mensagens, como todo o
 * resto da 002.
 */
export function paginaDePolitica(idiomaDaRota: AppLocale) {
  return async function Pagina({ params }: { params: Promise<{ locale: string }> }) {
    const { locale } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    const t = textos(idiomaDaRota).politica;

    return (
      <main className="mx-auto max-w-3xl px-4 py-12">
        <h1 data-testid={TESTID.politicaTitulo} className="text-3xl font-semibold tracking-tight">
          {t.titulo}
        </h1>

        <Secao titulo={t.quemPodeTitulo}>{t.quemPode}</Secao>

        <Secao titulo={t.verificadoTitulo}>
          {t.verificado}
          <span className="mt-3 block">{t.verificadoGratuito}</span>
        </Secao>

        <Secao titulo={t.moderacaoTitulo}>{t.moderacao}</Secao>

        <section className="mt-8">
          <h2 className="text-xl font-medium">{t.removidoTitulo}</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-neutral-700">
            {t.removidoItens.map((item) => (
              <li key={item} data-testid={TESTID.politicaRemovido}>
                {item}
              </li>
            ))}
          </ul>
        </section>

        <Secao titulo={t.idiomaTitulo}>{t.idioma}</Secao>

        <section className="mt-8" data-testid={TESTID.politicaContestacao}>
          <h2 className="text-xl font-medium">{t.contestacaoTitulo}</h2>
          <p className="mt-2 text-neutral-700">
            {/* O endereco sai num elemento proprio para o teste alcancar sem
                depender do texto ao redor, que muda com o idioma. */}
            {t.contestacao(CONTATO_DE_CONTESTACAO, PRAZO_DE_RESPOSTA)}
          </p>
          <p className="mt-2">
            <a
              href={`mailto:${CONTATO_DE_CONTESTACAO}`}
              data-testid={TESTID.politicaContestacaoEndereco}
              className="underline"
            >
              {CONTATO_DE_CONTESTACAO}
            </a>
          </p>
          <p
            data-testid={TESTID.politicaLimiteDeRemocao}
            className="mt-4 rounded-md border border-neutral-300 bg-neutral-50 px-4 py-3 text-neutral-800"
          >
            {t.contestacaoLimite}
          </p>
        </section>

        <Secao titulo={t.dadosTitulo}>{t.dados}</Secao>
      </main>
    );
  };
}

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section className="mt-8">
      <h2 className="text-xl font-medium">{titulo}</h2>
      <p className="mt-2 text-neutral-700">{children}</p>
    </section>
  );
}

export function metadataDePolitica(idiomaDaRota: AppLocale): Metadata {
  const t = textos(idiomaDaRota).politica;

  return metadataBilingue({
    locale: idiomaDaRota,
    caminhos: {
      "pt-BR": rotaPoliticaDeReviews("pt-BR"),
      en: rotaPoliticaDeReviews("en"),
    },
    titulo: t.titulo,
    descricao: t.quemPode,
  });
}
