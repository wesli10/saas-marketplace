/**
 * Os dois idiomas do produto, num tipo so.
 *
 * A constituicao secao 5 fixa `pt-BR` e `en` como os locales aceitos, e o
 * default como `pt-BR` - os mesmos valores que a API usa. Manter uma segunda
 * lista aqui seria uma segunda fonte da verdade; o que existe e este arquivo,
 * e a API rejeita o que nao reconhece.
 */
export const LOCALES = ["pt-BR", "en"] as const;

export type AppLocale = (typeof LOCALES)[number];

export const LOCALE_PADRAO: AppLocale = "pt-BR";

export function ehLocale(valor: string): valor is AppLocale {
  return (LOCALES as readonly string[]).includes(valor);
}

/** O outro idioma. Existe porque sao dois - com tres, isto vira uma lista. */
export function outroLocale(locale: AppLocale): AppLocale {
  return locale === "pt-BR" ? "en" : "pt-BR";
}
