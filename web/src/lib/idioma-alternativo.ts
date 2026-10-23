import { outroLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { comQuery, rotaBusca, rotaCategoria, rotaHome, rotaProduto } from "@/src/lib/routes";

/**
 * O mesmo lugar, no outro idioma (AC-05).
 *
 * Trocar de idioma nao pode jogar quem le na home: quem esta lendo a ficha do
 * Zapier em portugues quer a ficha do Zapier em ingles. O que muda entre as
 * paginas e de onde sai o endereco equivalente:
 *
 *  - **produto**: o slug e o mesmo nos dois idiomas (spec 001 secao 4.2), so o
 *    segmento da rota muda;
 *  - **categoria**: o slug MUDA, e quem tem o par e a API, no campo
 *    `alternates` da ficha da categoria (T07). Adivinhar o slug do outro idioma
 *    daria 404 silencioso;
 *  - **busca**: o termo e os filtros vao junto - trocar de idioma no meio de uma
 *    busca e continuar a mesma busca, e nao recomecar.
 *
 * A home e o fallback, e so isso: quando a categoria nao tem par, ir para a
 * home no outro idioma e melhor do que um link quebrado - mas e o unico caso em
 * que a troca de idioma perde o lugar.
 */
export type Destino =
  | { pagina: "home" }
  | { pagina: "produto"; slug: string }
  | { pagina: "categoria"; alternates?: Record<string, string>; query?: URLSearchParams }
  | { pagina: "busca"; query?: URLSearchParams };

export function noOutroIdioma(locale: AppLocale, destino: Destino): string {
  const alvo = outroLocale(locale);

  switch (destino.pagina) {
    case "produto":
      return rotaProduto(alvo, destino.slug);

    case "categoria": {
      const slug = destino.alternates?.[alvo];
      // Os filtros vao junto: os valores sao os da API (slug de integracao,
      // nome de enum, preco), iguais nos dois idiomas. Deixa-los para tras
      // faria a troca de idioma zerar a busca sem avisar.
      return slug ? comQuery(rotaCategoria(alvo, slug), destino.query) : rotaHome(alvo);
    }

    case "busca":
      return rotaBusca(alvo, destino.query);

    case "home":
      return rotaHome(alvo);
  }
}
