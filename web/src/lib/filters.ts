/**
 * Traducao entre a URL e a query da API, em funcao pura.
 *
 * Os nomes aqui sao **exatamente** os parametros da API (`integration`,
 * `pricing_model`, `has_free_trial`...). Nao ha mapeamento de nome nenhum de
 * proposito: o `name` de cada campo do formulario e o mesmo, entao sem
 * JavaScript o navegador monta a query certa sozinho, e o AC-09 sai do
 * mecanismo em vez de um caminho paralelo (plano DT-05).
 *
 * Os valores ficam como **texto cru**. O frontend nao "conserta" filtro
 * invalido: `pricing_model=XPTO` vai para a API, que ja responde 400, e a
 * pagina mostra o erro. Um so lugar valida.
 */
export const CAMPOS_MULTIPLOS = ["integration", "pricing_model"] as const;

/**
 * Estado que vive na URL e **nao** vai para a API.
 *
 * `comparar` e a selecao acumulada nos cards (spec secao 5.2). Ele precisa
 * sobreviver a filtrar e a paginar - senao marcar tres produtos e virar a
 * pagina apaga a selecao sem avisar -, e a forma de conseguir isso sem espalhar
 * um parametro solto por cada gerador de link e trata-lo como parte do mesmo
 * estado de URL, com uma marca dizendo que ele nao e query da API.
 */
export const CAMPOS_LOCAIS = ["comparar"] as const;
export const CAMPOS_SIMPLES = [
  "q",
  "has_free_trial",
  "has_free_plan",
  "price_max",
  "page",
  "size",
] as const;

export type CampoMultiplo = (typeof CAMPOS_MULTIPLOS)[number];
export type CampoLocal = (typeof CAMPOS_LOCAIS)[number];
export type CampoSimples = (typeof CAMPOS_SIMPLES)[number];

export type FiltroCatalogo = Partial<Record<CampoSimples, string>> &
  Record<CampoMultiplo, string[]> &
  Record<CampoLocal, string[]>;

export const FILTRO_VAZIO: FiltroCatalogo = { integration: [], pricing_model: [], comparar: [] };

/** Aceita o que o Next entrega em `searchParams`, e tambem `URLSearchParams`. */
export type EntradaDeBusca = URLSearchParams | Record<string, string | string[] | undefined>;

export function lerFiltro(entrada: EntradaDeBusca): FiltroCatalogo {
  const pegar = (chave: string): string[] => {
    if (entrada instanceof URLSearchParams) return entrada.getAll(chave);
    const valor = entrada[chave];
    if (valor === undefined) return [];
    return Array.isArray(valor) ? valor : [valor];
  };

  const filtro: FiltroCatalogo = { integration: [], pricing_model: [], comparar: [] };

  for (const campo of [...CAMPOS_MULTIPLOS, ...CAMPOS_LOCAIS]) {
    // Vazio nao entra: `?integration=` sem valor e um campo em branco enviado
    // pelo formulario, e nao um filtro por integracao chamada "".
    filtro[campo] = pegar(campo).filter((v) => v.trim() !== "");
  }

  for (const campo of CAMPOS_SIMPLES) {
    const valor = pegar(campo)[0];
    if (valor !== undefined && valor.trim() !== "") filtro[campo] = valor;
  }

  return filtro;
}

/**
 * O que vai para a API. Parametro repetido para os multiplos (plano DT-03).
 *
 * Os campos locais ficam de fora: `comparar` e selecao de tela, e mandar isso
 * para `GET /products` daria 400 num parametro que a API nao conhece.
 */
export function paraQueryDaApi(
  filtro: FiltroCatalogo,
): Record<string, string | string[] | undefined> {
  const query: Record<string, string | string[] | undefined> = {};

  for (const campo of CAMPOS_MULTIPLOS) {
    if (filtro[campo].length > 0) query[campo] = filtro[campo];
  }
  for (const campo of CAMPOS_SIMPLES) {
    if (filtro[campo] !== undefined) query[campo] = filtro[campo];
  }

  return query;
}

/** De volta para a URL - e a volta que prova que a ida nao perdeu nada. */
export function paraQueryDaUrl(filtro: FiltroCatalogo): URLSearchParams {
  const query = new URLSearchParams();

  for (const campo of [...CAMPOS_MULTIPLOS, ...CAMPOS_LOCAIS]) {
    for (const valor of filtro[campo]) query.append(campo, valor);
  }
  for (const campo of CAMPOS_SIMPLES) {
    const valor = filtro[campo];
    if (valor !== undefined) query.append(campo, valor);
  }

  return query;
}

export function temAlgumFiltro(filtro: FiltroCatalogo): boolean {
  return paraQueryDaUrl(filtro).toString() !== "";
}

/** O mesmo filtro sem um valor - e o que os controles de "limpar" usam. */
export function semValor(
  filtro: FiltroCatalogo,
  campo: CampoMultiplo | CampoSimples,
  valor?: string,
): FiltroCatalogo {
  const copia = clonar(filtro);

  if ((CAMPOS_MULTIPLOS as readonly string[]).includes(campo)) {
    const multiplo = campo as CampoMultiplo;
    copia[multiplo] = valor === undefined ? [] : copia[multiplo].filter((v) => v !== valor);
  } else {
    delete copia[campo as CampoSimples];
  }

  // Mexer em filtro sempre volta para a primeira pagina: manter `page=3` depois
  // de estreitar a busca costuma cair fora do intervalo e mostrar lista vazia.
  delete copia.page;
  return copia;
}

/**
 * O mesmo filtro noutra pagina - e o que os links de paginacao usam.
 *
 * A pagina 0 sai da URL em vez de virar `page=0`: a primeira pagina precisa de
 * uma URL so, senao a lista tem dois enderecos identicos - um com `page=0` e um
 * sem - e a canonica do T25 teria de escolher entre eles depois do fato.
 */
export function comPagina(filtro: FiltroCatalogo, pagina: number): FiltroCatalogo {
  const copia = clonar(filtro);

  if (pagina <= 0) delete copia.page;
  else copia.page = String(pagina);

  return copia;
}

/** Copia com as listas separadas: sem isto, mexer na copia mexeria no original. */
export function clonar(filtro: FiltroCatalogo): FiltroCatalogo {
  return {
    ...filtro,
    integration: [...filtro.integration],
    pricing_model: [...filtro.pricing_model],
    comparar: [...filtro.comparar],
  };
}
