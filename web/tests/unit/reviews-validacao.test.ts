import { describe, expect, it } from "vitest";

import {
  CORPO_MINIMO,
  validar,
  valoresDoFormData,
  VALORES_VAZIOS,
  type ValoresDoFormulario,
} from "@/src/lib/reviews/validacao";

/**
 * Criterio de verificacao da tarefa 003-T12: a regra do formulario, sem React,
 * sem Next e sem rede.
 *
 * A mesma funcao roda no servidor, que e onde ela precisa rodar: o formulario
 * funciona sem JavaScript (AC-17), entao validacao que so existisse no
 * navegador nao existiria no caminho que importa.
 */
const VALIDO: ValoresDoFormulario = {
  rating: "4",
  wouldRecommend: "true",
  title: "Resolveu o atendimento",
  body: "x".repeat(CORPO_MINIMO),
  authorName: "Ana Ribeiro",
  authorEmail: "ana@acme.com.br",
};

describe("validacao do formulario de avaliacao", () => {
  it("aceita o preenchimento completo", () => {
    expect(validar(VALIDO)).toEqual({});
  });

  it("cobra todos os campos quando o formulario vem vazio", () => {
    expect(validar(VALORES_VAZIOS)).toEqual({
      rating: "obrigatorio",
      wouldRecommend: "obrigatorio",
      title: "obrigatorio",
      body: "obrigatorio",
      authorName: "obrigatorio",
      authorEmail: "obrigatorio",
    });
  });

  it("nota fora de 1..5 e nota nao inteira sao invalidas", () => {
    expect(validar({ ...VALIDO, rating: "6" }).rating).toBe("notaInvalida");
    expect(validar({ ...VALIDO, rating: "0" }).rating).toBe("notaInvalida");
    expect(validar({ ...VALIDO, rating: "4.5" }).rating).toBe("notaInvalida");
    expect(validar({ ...VALIDO, rating: "cinco" }).rating).toBe("notaInvalida");
  });

  it("`recomendaria` sem resposta e erro: a formula do ranking precisa dele", () => {
    expect(validar({ ...VALIDO, wouldRecommend: "" }).wouldRecommend).toBe("obrigatorio");
    expect(validar({ ...VALIDO, wouldRecommend: "talvez" }).wouldRecommend).toBe("obrigatorio");
    // "false" e uma resposta, e nao ausencia de resposta.
    expect(validar({ ...VALIDO, wouldRecommend: "false" }).wouldRecommend).toBeUndefined();
  });

  it("o piso de 80 caracteres e contado sem os espacos das pontas", () => {
    const quaseLa = "x".repeat(CORPO_MINIMO - 1);

    expect(validar({ ...VALIDO, body: quaseLa }).body).toBe("corpoCurto");
    expect(validar({ ...VALIDO, body: `   ${quaseLa}   ` }).body).toBe("corpoCurto");
    expect(validar({ ...VALIDO, body: "x".repeat(2001) }).body).toBe("corpoLongo");
  });

  it("e-mail sem arroba ou sem dominio nao passa", () => {
    expect(validar({ ...VALIDO, authorEmail: "ana-arroba-acme" }).authorEmail).toBe(
      "emailInvalido",
    );
    expect(validar({ ...VALIDO, authorEmail: "ana@acme" }).authorEmail).toBe("emailInvalido");
    expect(validar({ ...VALIDO, authorEmail: "ana@acme.com.br" }).authorEmail).toBeUndefined();
  });

  it("le o FormData sem confiar nele: campo ausente vira texto vazio", () => {
    const dados = new FormData();
    dados.set("rating", "5");
    dados.set("body", "texto");

    expect(valoresDoFormData(dados)).toEqual({
      ...VALORES_VAZIOS,
      rating: "5",
      body: "texto",
    });
  });
});
