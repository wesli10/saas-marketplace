import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

/**
 * O selo de verificado (spec 003 secao 4.5).
 *
 * Ele diz uma coisa so, e e importante que diga apenas ela: "esta pessoa
 * confirmou um e-mail em @acme.com.br". Nao diz que a review e melhor, nem que
 * a pessoa e mais confiavel - review de dominio gratuito e aceita e aparece sem
 * selo, porque bloquear excluiria a PME que roda no Gmail.
 */
export function SeloVerificado({ locale, dominio }: { locale: AppLocale; dominio: string }) {
  const t = textos(locale);

  return (
    <span
      data-testid={TESTID.reviewSelo}
      data-dominio={dominio}
      title={t.reviews.seloTitulo}
      className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-800"
    >
      {t.reviews.selo(dominio)}
    </span>
  );
}
