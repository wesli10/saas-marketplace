/**
 * A regra de paginacao, em funcao pura.
 *
 * **A pagina na URL e a pagina da API: contada a partir de 0.** A tentacao e
 * mostrar `?page=1` para a primeira e subtrair um antes de chamar a API, mas
 * isso poe uma traducao de valor entre a URL e o backend - e a decisao do T17
 * foi justamente nao ter nenhuma (o `name` do campo e o parametro da API, e o
 * valor vai cru). Com a traducao, `page=0` e `page=1` virariam a mesma pagina
 * para quem digita a URL na mao, e o 400 da API chegaria deslocado. O que muda
 * e so o rotulo: quem le ve "Pagina 2 de 3" enquanto a URL diz `page=1`.
 *
 * A primeira pagina nao carrega `page=` nenhum (ver `comPagina`): uma so URL
 * canonica para ela, que o T25 vai precisar.
 */

/**
 * Itens por pagina quando a URL nao pede outro tamanho.
 *
 * Menor que o padrao da API (20) de proposito: o seed do 001 tem 20 produtos,
 * entao com 20 por pagina a paginacao nunca apareceria - e um caminho que
 * nenhum teste percorre e um caminho que nao existe. 12 tambem fecha a grade
 * de duas colunas sem sobra (plano, risco 5).
 */
export const SIZE_PADRAO = 12;

/** O que a resposta da lista informa sobre a fatia servida. */
export interface FatiaDaApi {
  page?: number;
  size?: number;
  total?: number;
}

export interface Paginacao {
  /** Base 0, como a API. */
  pagina: number;
  totalDePaginas: number;
  /** `null` quando nao ha para onde ir. */
  anterior: number | null;
  proxima: number | null;
}

export function paginacaoDe(fatia: FatiaDaApi): Paginacao {
  const size = fatia.size !== undefined && fatia.size > 0 ? fatia.size : SIZE_PADRAO;
  const total = Math.max(0, fatia.total ?? 0);
  const pagina = Math.max(0, fatia.page ?? 0);

  // Lista vazia continua sendo uma pagina - a que esta sendo vista.
  const totalDePaginas = Math.max(1, Math.ceil(total / size));

  return {
    pagina,
    totalDePaginas,
    // Fora da faixa (`?page=90`), "anterior" aponta para a ultima pagina que
    // existe, e nao para a vizinha vazia: quem chegou ali por URL torta quer
    // sair, e nao percorrer o vazio de tras para a frente.
    anterior: pagina > 0 ? Math.min(pagina, totalDePaginas) - 1 : null,
    proxima: pagina + 1 < totalDePaginas ? pagina + 1 : null,
  };
}
