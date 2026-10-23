import { VALORES_VAZIOS, type ErrosDoFormulario, type ValoresDoFormulario } from "./validacao";

/**
 * O estado que o formulario recebe de volta do servidor.
 *
 * Mora aqui, e nao junto da Server Action, por uma regra do Next: num modulo
 * com `"use server"` no topo, **todo export tem de ser uma funcao assincrona**.
 * Exportar esta constante de la fazia o `ESTADO_INICIAL` chegar `undefined` ao
 * componente, e a tela quebrava com "Cannot convert undefined or null to
 * object" - em tempo de execucao, no servidor, e nao no build.
 *
 * `valores` volta **sempre** que ha erro, e e o ponto do AC-17: perder 800
 * caracteres de texto por causa de um e-mail digitado errado e o tipo de coisa
 * que faz a pessoa desistir e nao voltar.
 */
export interface EstadoDoFormulario {
  erros: ErrosDoFormulario;
  /** Erro que nao e de campo: a API recusou, ou nao respondeu. */
  falha: "limite" | "invalido" | "indisponivel" | null;
  valores: ValoresDoFormulario;
}

export const ESTADO_INICIAL: EstadoDoFormulario = {
  erros: {},
  falha: null,
  valores: VALORES_VAZIOS,
};

/**
 * O estado do formulario de reenvio (spec 003 secao 5.1).
 *
 * `enviado` nao quer dizer que existia algo para reenviar: a API responde 202
 * sempre, e a tela repete essa ambiguidade de proposito - dizer "nao existe
 * avaliacao para este e-mail" transformaria a tela numa sonda de quem avaliou o
 * que.
 */
export interface EstadoDoReenvio {
  enviado: boolean;
  falha: boolean;
  email: string;
}

export const REENVIO_INICIAL: EstadoDoReenvio = { enviado: false, falha: false, email: "" };
