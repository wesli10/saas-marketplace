/**
 * A validacao do formulario de avaliacao, em funcao pura (plano DT-10).
 *
 * Sem React, sem Next e sem rede: da para exercitar cada regra num teste de
 * milissegundos, e a mesma funcao roda no servidor - e nao no navegador -,
 * porque o formulario precisa funcionar sem JavaScript (AC-17).
 *
 * **Isto nao substitui a validacao da API**, e nao tenta. A borda que manda e a
 * do backend (constituicao secao 6); esta existe para devolver o erro na tela
 * com o texto que a pessoa escreveu, em vez de um 400 cru.
 */
export const CORPO_MINIMO = 80;
export const CORPO_MAXIMO = 2000;
export const TITULO_MAXIMO = 120;
export const NOME_MAXIMO = 80;

/** O que o formulario coleta, sempre como texto - e o que um `<form>` entrega. */
export interface ValoresDoFormulario {
  rating: string;
  wouldRecommend: string;
  title: string;
  body: string;
  authorName: string;
  authorEmail: string;
}

export type CampoDoFormulario = keyof ValoresDoFormulario;

/** A chave do erro, e nao o texto: quem traduz e a tela, no idioma da rota. */
export type ChaveDeErro =
  | "obrigatorio"
  | "notaInvalida"
  | "corpoCurto"
  | "corpoLongo"
  | "tituloLongo"
  | "nomeLongo"
  | "emailInvalido";

export type ErrosDoFormulario = Partial<Record<CampoDoFormulario, ChaveDeErro>>;

export const VALORES_VAZIOS: ValoresDoFormulario = {
  rating: "",
  wouldRecommend: "",
  title: "",
  body: "",
  authorName: "",
  authorEmail: "",
};

/**
 * Deliberadamente simples: um `@`, algo antes, algo depois com um ponto.
 *
 * Validar e-mail por expressao regular "de verdade" e uma armadilha conhecida -
 * a RFC 5322 aceita coisas que ninguem espera, e toda regex famosa recusa
 * endereco valido de alguem. Quem diz a palavra final e a API, e depois dela o
 * proprio servidor de e-mail: o endereco que nao existe simplesmente nunca
 * recebe o link.
 */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validar(valores: ValoresDoFormulario): ErrosDoFormulario {
  const erros: ErrosDoFormulario = {};

  const nota = Number(valores.rating);
  if (!valores.rating.trim()) erros.rating = "obrigatorio";
  else if (!Number.isInteger(nota) || nota < 1 || nota > 5) erros.rating = "notaInvalida";

  // "Recomendaria?" e obrigatorio de proposito (spec secao 4.1): sem ele a
  // formula de ranking publicada no plano de produto nao fecha.
  if (valores.wouldRecommend !== "true" && valores.wouldRecommend !== "false") {
    erros.wouldRecommend = "obrigatorio";
  }

  const titulo = valores.title.trim();
  if (!titulo) erros.title = "obrigatorio";
  else if (titulo.length > TITULO_MAXIMO) erros.title = "tituloLongo";

  const corpo = valores.body.trim();
  if (!corpo) erros.body = "obrigatorio";
  else if (corpo.length < CORPO_MINIMO) erros.body = "corpoCurto";
  else if (corpo.length > CORPO_MAXIMO) erros.body = "corpoLongo";

  const nome = valores.authorName.trim();
  if (!nome) erros.authorName = "obrigatorio";
  else if (nome.length > NOME_MAXIMO) erros.authorName = "nomeLongo";

  const email = valores.authorEmail.trim();
  if (!email) erros.authorEmail = "obrigatorio";
  else if (!EMAIL.test(email)) erros.authorEmail = "emailInvalido";

  return erros;
}

/** Le o que veio do `<form>`, sem confiar em nada: campo ausente vira texto vazio. */
export function valoresDoFormData(dados: FormData): ValoresDoFormulario {
  const texto = (campo: string) => {
    const valor = dados.get(campo);
    return typeof valor === "string" ? valor : "";
  };

  return {
    rating: texto("rating"),
    wouldRecommend: texto("would_recommend"),
    title: texto("title"),
    body: texto("body"),
    authorName: texto("author_name"),
    authorEmail: texto("author_email"),
  };
}
