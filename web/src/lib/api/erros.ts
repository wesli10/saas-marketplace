import "server-only";

import { notFound } from "next/navigation";

import { ApiError } from "./client";

/**
 * Traduz erro da API em comportamento de pagina (plano DT-07).
 *
 * O mapeamento acontece aqui, no servidor, e nao no `error.tsx`: em producao o
 * Next apaga a mensagem do erro antes de entrega-la ao boundary, deixando so um
 * digest. Decidir "isto e 404" la seria decidir sem informacao.
 *
 * 404 vira `notFound()`, que responde **HTTP 404** de verdade. Uma pagina vazia
 * com status 200 e pior que um erro: o rastreador indexa o vazio.
 */
export function tratarErroDaApi(erro: unknown): never {
  if (erro instanceof ApiError && erro.status === 404) {
    notFound();
  }
  throw erro;
}
