import Link from "next/link";

import { ListaDeReviews } from "@/src/components/reviews/ListaDeReviews";
import { NotaResumo } from "@/src/components/reviews/NotaResumo";
import type { ListaDeReviews as Lista } from "@/src/lib/api/reviews";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaAvaliar, rotaPoliticaDeReviews } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * O bloco de avaliacoes da ficha (spec 003 secao 6).
 *
 * `lista` nula significa que a chamada falhou. A ficha **nao** cai junto: as
 * avaliacoes sao conteudo adicional, e derrubar a pagina inteira do produto
 * porque um endpoint secundario piscou seria trocar uma perda pequena por uma
 * grande. O aviso aparece na tela em vez de o bloco sumir em silencio - some
 * seria indistinguivel de "ninguem avaliou".
 *
 * Sem distribuicao por estrela, sem filtro e sem ordenacao: os tres so
 * significam alguma coisa com volume, e com cinco avaliacoes viram enfeite
 * (spec secao 6).
 */
export function BlocoDeReviews({
  lista,
  locale,
  base,
  slug,
}: {
  lista: Lista | null;
  locale: AppLocale;
  base: string;
  slug: string;
}) {
  const t = textos(locale);

  return (
    <section
      data-testid={TESTID.reviewsBloco}
      className="mx-auto mt-12 max-w-3xl border-t border-neutral-200 px-4 pt-10"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-xl font-medium">{t.reviews.titulo}</h2>

        {/* Da lastro ao selo: quem ve "verificado" precisa poder ler o que isso
            significa e o que a plataforma remove (spec secao 7.1). */}
        <Link
          href={rotaPoliticaDeReviews(locale)}
          data-testid={TESTID.reviewsPolitica}
          className="text-sm underline"
        >
          {t.reviews.politica}
        </Link>
      </div>

      {lista === null ? (
        <p
          role="status"
          data-testid={TESTID.reviewsIndisponivel}
          className="mt-6 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        >
          {t.reviews.indisponivel}
        </p>
      ) : (
        <>
          <div className="mt-6">
            <NotaResumo nota={lista.rating ?? null} locale={locale} />
          </div>
          <ListaDeReviews lista={lista} locale={locale} base={base} />
        </>
      )}

      {/* O convite fica DEPOIS da lista: quem chega na ficha vem ler, e o
          pedido para escrever faz mais sentido depois de ver o que existe. */}
      <p className="mt-8">
        <Link
          href={rotaAvaliar(locale, slug)}
          data-testid={TESTID.fichaAvaliar}
          className="inline-block rounded-md border border-neutral-900 px-4 py-2 font-medium"
        >
          {t.reviews.avaliar}
        </Link>
      </p>
    </section>
  );
}
