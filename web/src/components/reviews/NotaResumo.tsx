import type { NotaDoProduto } from "@/src/lib/api/reviews";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

/**
 * A nota do produto (spec 003 secao 4.4).
 *
 * `nota` nula e um estado de verdade, e nao um erro: produto sem avaliacao
 * publicada nao tem nota, e nota zero seria uma avaliacao pessima. A tela diz
 * isso com todas as letras, em vez de mostrar "0,0" ou um espaco em branco.
 *
 * A media e formatada pelo `Intl` do idioma da pagina: `4.3` em ingles e `4,3`
 * em portugues, sem tabela nossa de separador decimal.
 */
export function NotaResumo({ nota, locale }: { nota: NotaDoProduto | null; locale: AppLocale }) {
  const t = textos(locale);

  if (!nota || !nota.count) {
    return (
      <p data-testid={TESTID.reviewsVazio} className="text-neutral-600">
        {t.reviews.semNota} {t.reviews.sejaOPrimeiro}
      </p>
    );
  }

  const media = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(nota.average ?? 0);

  return (
    <div data-testid={TESTID.reviewsNota} className="flex flex-wrap items-baseline gap-3">
      <p
        data-testid={TESTID.reviewsMedia}
        data-media={nota.average}
        className="text-3xl font-semibold"
      >
        {media}
        <span className="text-base font-normal text-neutral-500">/5</span>
      </p>

      <p data-testid={TESTID.reviewsContagem} data-total={nota.count} className="text-neutral-600">
        {t.reviews.contagem(nota.count)}
      </p>

      {nota.verified_count ? (
        <p
          data-testid={TESTID.reviewsVerificadas}
          data-verificadas={nota.verified_count}
          className="text-sm text-neutral-500"
        >
          {t.reviews.verificadas(nota.verified_count)}
        </p>
      ) : null}
    </div>
  );
}
