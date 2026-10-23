/**
 * A URL publica do site, para as canonicas e o JSON-LD.
 *
 * Canonica **absoluta** e exigencia do AC-06: caminho relativo em
 * `rel=canonical` e ambiguo para o rastreador quando a mesma pagina responde em
 * mais de um host (preview, dominio antigo, IP). Como o valor muda por
 * ambiente, ele vem do ambiente - e nao do codigo.
 *
 * O default aponta para o compose local, que e onde o e2e roda. Em producao,
 * `SITE_URL` e obrigatoria na pratica: sem ela o site publicaria canonicas
 * apontando para `localhost`.
 */
const SITE_URL = process.env.SITE_URL ?? "http://localhost:3000";

export function urlDoSite(): string {
  return SITE_URL.replace(/\/+$/, "");
}

export function urlAbsoluta(caminho: string): string {
  return `${urlDoSite()}${caminho.startsWith("/") ? caminho : `/${caminho}`}`;
}
