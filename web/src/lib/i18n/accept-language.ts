import { LOCALE_PADRAO, LOCALES, type AppLocale } from "./locales";

/**
 * Escolhe o idioma a partir do cabecalho `Accept-Language`.
 *
 * Escrito a mao, e nao com `negotiator` ou `intl-localematcher`: sao dois
 * idiomas e uma regra de desempate. Uma dependencia para isso seria mais codigo
 * para auditar do que o que ela substitui.
 *
 * A negociacao acontece por *subtag primaria*: `pt-PT` e `pt-BR` sao ambos
 * portugues, e servir portugues do Brasil a quem pediu portugues de Portugal e
 * melhor que servir ingles. Idioma que nao conhecemos nao e erro - cai no
 * padrao, como a spec 002 secao 4 pede.
 */
export function idiomaPreferido(cabecalho: string | null | undefined): AppLocale {
  if (!cabecalho) return LOCALE_PADRAO;

  const candidatos = cabecalho
    .split(",")
    .map((parte) => {
      const [tag = "", ...parametros] = parte.trim().split(";");
      const q = parametros
        .map((p) => p.trim())
        .find((p) => p.startsWith("q="))
        ?.slice(2);

      const peso = q === undefined ? 1 : Number.parseFloat(q);
      return { tag: tag.trim().toLowerCase(), peso: Number.isNaN(peso) ? 0 : peso };
    })
    // Peso 0 significa "nao quero este", e nao "tanto faz".
    .filter((c) => c.tag !== "" && c.peso > 0)
    .sort((a, b) => b.peso - a.peso);

  for (const candidato of candidatos) {
    if (candidato.tag === "*") return LOCALE_PADRAO;

    const primaria = candidato.tag.split("-")[0];
    const encontrado = LOCALES.find((locale) => locale.toLowerCase().split("-")[0] === primaria);
    if (encontrado) return encontrado;
  }

  return LOCALE_PADRAO;
}
