import { describe, expect, it } from "vitest";

import {
  FILTRO_VAZIO,
  lerFiltro,
  paraQueryDaApi,
  paraQueryDaUrl,
  semValor,
  temAlgumFiltro,
} from "@/src/lib/filters";

/** Criterio de verificacao da tarefa 002-T17: a ida e a volta da query. */
describe("filtros", () => {
  it("le multiplos como lista e simples como texto", () => {
    const filtro = lerFiltro(
      new URLSearchParams("integration=whatsapp&integration=slack&has_free_plan=true"),
    );

    expect(filtro.integration).toEqual(["whatsapp", "slack"]);
    expect(filtro.has_free_plan).toBe("true");
    expect(filtro.pricing_model).toEqual([]);
  });

  it("aceita tambem o formato de searchParams do Next", () => {
    const filtro = lerFiltro({ integration: ["whatsapp"], q: "chatbot", size: undefined });

    expect(filtro.integration).toEqual(["whatsapp"]);
    expect(filtro.q).toBe("chatbot");
    expect(filtro.size).toBeUndefined();
  });

  it("campo em branco nao vira filtro", () => {
    // O formulario envia `?q=&integration=` quando os campos estao vazios.
    const filtro = lerFiltro(new URLSearchParams("q=&integration=&price_max=  "));

    expect(temAlgumFiltro(filtro)).toBe(false);
  });

  it("ida e volta preserva a query", () => {
    const original =
      "integration=whatsapp&integration=slack&pricing_model=FREEMIUM&has_free_trial=true&price_max=100";

    expect(paraQueryDaUrl(lerFiltro(new URLSearchParams(original))).toString()).toBe(
      new URLSearchParams(original).toString(),
    );
  });

  it("nao conserta valor invalido: quem valida e a API", () => {
    // `pricing_model=XPTO` chega inteiro na query - a API responde 400, e a
    // pagina mostra o erro. Corrigir aqui criaria uma segunda regra de validacao.
    const filtro = lerFiltro(new URLSearchParams("pricing_model=XPTO&price_max=abc"));

    expect(paraQueryDaApi(filtro)).toMatchObject({ pricing_model: ["XPTO"], price_max: "abc" });
  });

  it("multiplo vira parametro repetido na query da API", () => {
    const filtro = lerFiltro(new URLSearchParams("integration=a&integration=b"));

    expect(paraQueryDaApi(filtro).integration).toEqual(["a", "b"]);
  });

  it("limpar um valor tira so ele, e volta para a primeira pagina", () => {
    const filtro = lerFiltro(new URLSearchParams("integration=a&integration=b&page=3"));

    const semA = semValor(filtro, "integration", "a");
    expect(semA.integration).toEqual(["b"]);
    // Manter page=3 depois de estreitar a busca costuma cair fora do intervalo.
    expect(semA.page).toBeUndefined();
  });

  it("limpar sem valor tira o campo inteiro", () => {
    const filtro = lerFiltro(new URLSearchParams("integration=a&integration=b&has_free_plan=true"));

    expect(semValor(filtro, "integration").integration).toEqual([]);
    expect(semValor(filtro, "has_free_plan").has_free_plan).toBeUndefined();
  });

  it("limpar nao altera o filtro original", () => {
    const filtro = lerFiltro(new URLSearchParams("integration=a"));
    semValor(filtro, "integration");

    expect(filtro.integration).toEqual(["a"]);
  });

  it("filtro vazio nao tem query", () => {
    expect(paraQueryDaUrl(FILTRO_VAZIO).toString()).toBe("");
    expect(temAlgumFiltro(FILTRO_VAZIO)).toBe(false);
  });
});
