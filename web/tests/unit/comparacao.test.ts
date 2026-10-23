import { describe, expect, it } from "vitest";

import {
  alternarSelecao,
  comSelecaoAlternada,
  faixaValida,
  lerSlugs,
  MAXIMO_PARA_COMPARAR,
  semSelecao,
} from "@/src/lib/comparacao";
import { lerFiltro } from "@/src/lib/filters";

/** Criterio de verificacao da tarefa 002-T24: AC-16 e AC-17. */
describe("selecao para comparar", () => {
  it("le a lista separada por virgula, ignorando vazio e espaco", () => {
    expect(lerSlugs("zapier, make ,,n8n")).toEqual(["zapier", "make", "n8n"]);
    expect(lerSlugs(undefined)).toEqual([]);
    expect(lerSlugs("")).toEqual([]);
  });

  it("a faixa e a mesma da API: de 2 a 3", () => {
    expect(faixaValida([])).toBe(false);
    expect(faixaValida(["a"])).toBe(false);
    expect(faixaValida(["a", "b"])).toBe(true);
    expect(faixaValida(["a", "b", "c"])).toBe(true);
    expect(faixaValida(["a", "b", "c", "d"])).toBe(false);
  });

  it("alternar marca e desmarca", () => {
    expect(alternarSelecao([], "zapier")).toEqual(["zapier"]);
    expect(alternarSelecao(["zapier", "make"], "zapier")).toEqual(["make"]);
  });

  it("no limite, o quarto nao entra - e nenhum sai em silencio", () => {
    // Empurrar o mais antigo para fora removeria algo que a pessoa escolheu,
    // sem ela pedir.
    const cheia = ["a", "b", "c"];
    expect(cheia).toHaveLength(MAXIMO_PARA_COMPARAR);

    expect(alternarSelecao(cheia, "d")).toEqual(cheia);
    // Desmarcar continua funcionando no limite.
    expect(alternarSelecao(cheia, "b")).toEqual(["a", "c"]);
  });

  it("o href do card leva os filtros junto e nao vaza para a API", () => {
    const filtro = lerFiltro(new URLSearchParams("has_free_plan=true&comparar=make"));

    const href = comSelecaoAlternada("/pt-BR/categorias/x", filtro, "zapier");

    expect(href).toContain("has_free_plan=true");
    expect(href).toContain("comparar=make");
    expect(href).toContain("comparar=zapier");
  });

  it("limpar a selecao preserva o resto da URL", () => {
    const filtro = lerFiltro(
      new URLSearchParams("has_free_plan=true&comparar=make&comparar=zapier"),
    );

    expect(semSelecao("/pt-BR/categorias/x", filtro)).toBe(
      "/pt-BR/categorias/x?has_free_plan=true",
    );
  });
});
