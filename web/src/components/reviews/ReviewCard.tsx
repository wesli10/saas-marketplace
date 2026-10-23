import { SeloVerificado } from "@/src/components/reviews/SeloVerificado";
import type { Review } from "@/src/lib/api/reviews";
import { dataFormatada, nomeDoIdioma } from "@/src/lib/format";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

/**
 * Uma review publicada (spec 003 secao 6).
 *
 * O caso que mais importa aqui e o do idioma. Uma review existe num idioma so -
 * o de quem escreveu (spec secao 4.3) -, e as do outro idioma **aparecem**, e
 * nao sao escondidas: num catalogo bilingue de mercado pequeno, esconde-las
 * jogaria metade do conteudo fora. O que a tela faz e marcar a diferenca, e
 * nunca traduzir - traduzir seria por palavras na boca de uma pessoa
 * identificavel (constituicao secao 7).
 *
 * A marcacao nao e so visual: o texto vai dentro de um elemento com `lang`, que
 * e o que faz um leitor de tela trocar de pronuncia em vez de ler ingles com
 * fonemas de portugues.
 */
export function ReviewCard({ review, locale }: { review: Review; locale: AppLocale }) {
  const t = textos(locale);

  const idiomaDaReview = review.locale ?? locale;
  const outroIdioma = ehLocale(idiomaDaReview) && idiomaDaReview !== locale;
  const publicada = dataFormatada(locale, review.published_at);

  return (
    <article
      data-testid={TESTID.reviewCard}
      data-locale={idiomaDaReview}
      className="border-t border-neutral-200 py-6 first:border-t-0 first:pt-0"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span
          data-testid={TESTID.reviewNota}
          data-nota={review.rating}
          aria-label={t.reviews.nota(review.rating ?? 0)}
          className="rounded-md bg-neutral-900 px-2 py-0.5 text-sm font-semibold text-white"
        >
          {review.rating}/5
        </span>

        {review.verified && review.author_domain ? (
          <SeloVerificado locale={locale} dominio={review.author_domain} />
        ) : null}

        {outroIdioma ? (
          <span
            data-testid={TESTID.reviewIdioma}
            data-idioma={idiomaDaReview}
            className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-700"
          >
            {t.reviews.escritaEm(nomeDoIdioma(locale, idiomaDaReview.split("-")[0] ?? "en"))}
          </span>
        ) : null}
      </div>

      <h3 data-testid={TESTID.reviewTitulo} lang={idiomaDaReview} className="mt-3 font-medium">
        {review.title}
      </h3>

      <p
        data-testid={TESTID.reviewCorpo}
        lang={idiomaDaReview}
        className="mt-1 whitespace-pre-line text-neutral-700"
      >
        {review.body}
      </p>

      <p data-testid={TESTID.reviewAutor} className="mt-3 text-sm text-neutral-500">
        {review.author_name}
        {publicada ? ` · ${publicada}` : null}
      </p>
    </article>
  );
}
