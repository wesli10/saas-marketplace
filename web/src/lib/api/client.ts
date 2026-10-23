import "server-only";

import type { AppLocale } from "@/src/lib/i18n/locales";

/**
 * O unico ponto que fala com a API.
 *
 * `server-only` no topo nao e decorativo: ele quebra o build se este modulo for
 * importado de um componente de cliente, e e assim que `API_BASE_URL` fica fora
 * do bundle (AC-24). A guarda protege o import - passar o valor como prop para
 * um componente de cliente ainda vazaria pelo payload de hidratacao, e por isso
 * o AC inspeciona o HTML servido, e nao so o bundle.
 */
const BASE = process.env.API_BASE_URL ?? "http://localhost:8080";
const TIMEOUT_MS = Number(process.env.API_TIMEOUT_MS ?? 5000);

/** Problem Details da RFC 9457, como a API do 001 responde. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { field?: string; code?: string; message?: string }[];
  missing_slugs?: string[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problema: ProblemDetails | null,
    readonly rota: string,
  ) {
    super(`API respondeu ${status} em ${rota}`);
    this.name = "ApiError";
  }
}

/** Rede fora, DNS, timeout: nao ha resposta, e isso e diferente de um 5xx. */
export class ApiIndisponivelError extends Error {
  constructor(
    readonly rota: string,
    readonly causa: unknown,
  ) {
    super(`API nao respondeu em ${rota}`);
    this.name = "ApiIndisponivelError";
  }
}

export interface OpcoesDeBusca {
  locale: AppLocale;
  query?: Record<string, string | number | boolean | string[] | undefined | null>;
}

export async function buscarNaApi<T>(rota: string, opcoes: OpcoesDeBusca): Promise<T> {
  const url = new URL(`${BASE}${rota}`);
  for (const [chave, valor] of Object.entries(opcoes.query ?? {})) {
    if (valor === undefined || valor === null) continue;
    // Parametro repetido, e nao lista separada por virgula: e o formato que a
    // API do 001 espera para `integration` e `pricing_model`.
    if (Array.isArray(valor)) {
      for (const item of valor) url.searchParams.append(chave, item);
    } else {
      url.searchParams.append(chave, String(valor));
    }
  }

  const controle = new AbortController();
  const relogio = setTimeout(() => controle.abort(), TIMEOUT_MS);

  let resposta: Response;
  try {
    resposta = await fetch(url, {
      // O idioma vai so pelo cabecalho. A API aceita `?lang=` tambem, e usar os
      // dois criaria duas formas de dizer a mesma coisa - e um caso de
      // desacordo entre elas.
      headers: { "Accept-Language": opcoes.locale },
      signal: controle.signal,
      cache: "no-store",
    });
  } catch (causa) {
    throw new ApiIndisponivelError(rota, causa);
  } finally {
    clearTimeout(relogio);
  }

  if (!resposta.ok) {
    throw new ApiError(resposta.status, await lerProblema(resposta), rota);
  }

  return (await resposta.json()) as T;
}

/**
 * Escrita na API (spec 003 secao 5.1). O primeiro POST desta aplicacao.
 *
 * Mesma politica de erro do GET: 4xx e 5xx viram {@link ApiError}, e quem chama
 * decide o que fazer. A Server Action do formulario traduz 400 em erro de campo
 * e 429 em "espere um pouco"; nenhuma das duas decisoes cabe aqui.
 *
 * Sem `cache` nenhum, ao contrario do GET: POST nao passa por cache do Next.
 */
export async function enviarParaApi(
  rota: string,
  opcoes: { locale: AppLocale; corpo: unknown },
): Promise<void> {
  const controle = new AbortController();
  const relogio = setTimeout(() => controle.abort(), TIMEOUT_MS);

  let resposta: Response;
  try {
    resposta = await fetch(`${BASE}${rota}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": opcoes.locale,
      },
      body: JSON.stringify(opcoes.corpo),
      signal: controle.signal,
    });
  } catch (causa) {
    throw new ApiIndisponivelError(rota, causa);
  } finally {
    clearTimeout(relogio);
  }

  if (!resposta.ok) {
    throw new ApiError(resposta.status, await lerProblema(resposta), rota);
  }
}

/** Erro sem corpo, ou com corpo que nao e JSON, nao pode virar outra excecao. */
async function lerProblema(resposta: Response): Promise<ProblemDetails | null> {
  try {
    return (await resposta.json()) as ProblemDetails;
  } catch {
    return null;
  }
}
