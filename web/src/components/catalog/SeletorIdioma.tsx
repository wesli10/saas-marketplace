import Link from "next/link";

import { textos } from "@/src/lib/i18n";
import { outroLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { noOutroIdioma, type Destino } from "@/src/lib/idioma-alternativo";
import { TESTID } from "@/src/lib/testid";

/**
 * Um link, e nao um `<select>` com JavaScript: sao dois idiomas, entao a troca
 * e sempre "ir para a outra versao". Link funciona sem script, o robo de busca
 * segue, e o `hreflang` diz para onde vai.
 *
 * O destino vem da pagina, porque so ela sabe o que esta mostrando - a
 * categoria precisa do `alternates` da API, a busca precisa levar a query
 * junto. A regra de qual endereco e o equivalente esta em `idioma-alternativo`,
 * num lugar so.
 */
export function SeletorIdioma({ locale, destino }: { locale: AppLocale; destino: Destino }) {
  const t = textos(locale);
  const alvo = outroLocale(locale);

  return (
    <Link
      href={noOutroIdioma(locale, destino)}
      hrefLang={alvo}
      lang={alvo}
      data-testid={TESTID.seletorIdioma}
      data-locale={alvo}
      className="text-sm underline underline-offset-4 hover:no-underline"
    >
      {t.navegacao.trocarIdioma}
    </Link>
  );
}
