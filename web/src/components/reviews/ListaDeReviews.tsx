import Link from "next/link";

import { ReviewCard } from "@/src/components/reviews/ReviewCard";
import type { ListaDeReviews as Lista } from "@/src/lib/api/reviews";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { paginacaoDe } from "@/src/lib/paginacao";
import { comQuery } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/** O parametro que a ficha usa para paginar as avaliacoes. */
export const PARAM_DE_PAGINA = "avaliacoes";

/**
 * As avaliacoes publicadas, da mais recente para a mais antiga (AC-11).
 *
 * A paginacao e link de verdade, como a do catalogo: o estado vive na URL
 * (constituicao secao 4), entao a pagina 2 das avaliacoes e compartilhavel e
 * sobrevive ao botao "voltar". O parametro tem nome proprio (`avaliacoes=`)
 * porque a ficha ja pode ganhar outra lista paginada um dia, e `page=` generico
 * ficaria ambiguo.
 */
export function ListaDeReviews({
  lista,
  locale,
  base,
}: {
  lista: Lista;
  locale: AppLocale;
  base: string;
}) {
  const t = textos(locale);
  const itens = lista.items ?? [];

  if (itens.length === 0) return null;

  const { pagina, totalDePaginas, anterior, proxima } = paginacaoDe(lista);
  const href = (destino: number) =>
    comQuery(base, destino === 0 ? "" : `${PARAM_DE_PAGINA}=${destino}`);

  return (
    <>
      <div className="mt-6">
        {itens.map((review) => (
          <ReviewCard key={review.id} review={review} locale={locale} />
        ))}
      </div>

      {totalDePaginas > 1 ? (
        <nav
          aria-label={t.reviews.paginacao.navegacao}
          data-testid={TESTID.reviewsPaginacao}
          className="mt-6 flex items-center justify-between gap-4 border-t border-neutral-200 pt-4"
        >
          {anterior === null ? (
            <span />
          ) : (
            <Link
              href={href(anterior)}
              rel="prev"
              data-testid={TESTID.reviewsPaginacaoAnterior}
              className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:border-neutral-500"
            >
              {t.reviews.paginacao.anterior}
            </Link>
          )}

          <span className="text-sm text-neutral-500">
            {t.paginacao.status(Math.min(pagina, totalDePaginas - 1) + 1, totalDePaginas)}
          </span>

          {proxima === null ? (
            <span />
          ) : (
            <Link
              href={href(proxima)}
              rel="next"
              data-testid={TESTID.reviewsPaginacaoProxima}
              className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:border-neutral-500"
            >
              {t.reviews.paginacao.proxima}
            </Link>
          )}
        </nav>
      ) : null}
    </>
  );
}
