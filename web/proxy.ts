import { NextResponse, type NextRequest } from "next/server";

import { idiomaPreferido } from "@/src/lib/i18n/accept-language";

/**
 * Faz uma coisa so: manda a raiz para o idioma do visitante.
 *
 * Chama-se `proxy.ts` e nao `middleware.ts` porque o Next 16 renomeou a
 * convencao - o nome antigo ainda funciona, mas avisa a cada build.
 *
 * Middleware roda em toda requisicao que casa com o matcher, e e o lugar mais
 * caro do Next para colocar logica. Por isso o matcher e literalmente "/" - a
 * validacao do idioma acontece no layout do segmento (T09), onde ela custa uma
 * comparacao de string por render em vez de uma execucao de middleware por
 * requisicao.
 *
 * 307 e nao 308: preserva o metodo e nao pede para o navegador memorizar o
 * destino. O idioma preferido do visitante pode mudar, e um redirect permanente
 * na raiz e o tipo de coisa que fica presa em cache por meses.
 */
export default function proxy(request: NextRequest) {
  const locale = idiomaPreferido(request.headers.get("accept-language"));
  return NextResponse.redirect(new URL(`/${locale}`, request.url), 307);
}

export const config = {
  matcher: "/",
};
