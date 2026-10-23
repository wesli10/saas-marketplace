import { describe, expect, it } from "vitest";

import { noOutroIdioma } from "@/src/lib/idioma-alternativo";

/** Criterio de verificacao da tarefa 002-T23: AC-05. */
describe("o mesmo lugar no outro idioma", () => {
  it("produto: mesmo slug, segmento traduzido", () => {
    // Slug de produto nao muda (spec 001 secao 4.2) - so a rota.
    expect(noOutroIdioma("pt-BR", { pagina: "produto", slug: "zapier" })).toBe(
      "/en/products/zapier",
    );
    expect(noOutroIdioma("en", { pagina: "produto", slug: "zapier" })).toBe(
      "/pt-BR/produtos/zapier",
    );
  });

  it("categoria: o slug vem do alternates da API, e nao de adivinhacao", () => {
    const alternates = { "pt-BR": "agentes-e-workflows", en: "agents-and-workflows" };

    expect(noOutroIdioma("pt-BR", { pagina: "categoria", alternates })).toBe(
      "/en/categories/agents-and-workflows",
    );
    expect(noOutroIdioma("en", { pagina: "categoria", alternates })).toBe(
      "/pt-BR/categorias/agentes-e-workflows",
    );
  });

  it("categoria sem par cai na home do outro idioma, e nao num link quebrado", () => {
    expect(noOutroIdioma("pt-BR", { pagina: "categoria", alternates: undefined })).toBe("/en");
    expect(
      noOutroIdioma("pt-BR", { pagina: "categoria", alternates: { "pt-BR": "so-em-portugues" } }),
    ).toBe("/en");
  });

  it("categoria leva os filtros junto", () => {
    const alternates = { "pt-BR": "agentes-e-workflows", en: "agents-and-workflows" };
    const query = new URLSearchParams("has_free_plan=true&page=2");

    expect(noOutroIdioma("pt-BR", { pagina: "categoria", alternates, query })).toBe(
      "/en/categories/agents-and-workflows?has_free_plan=true&page=2",
    );
  });

  it("busca leva o termo junto: trocar de idioma nao e recomecar a busca", () => {
    const query = new URLSearchParams("q=chatbot");

    expect(noOutroIdioma("pt-BR", { pagina: "busca", query })).toBe("/en/search?q=chatbot");
    expect(noOutroIdioma("en", { pagina: "busca", query: new URLSearchParams() })).toBe(
      "/pt-BR/buscar",
    );
  });

  it("home vai para a home", () => {
    expect(noOutroIdioma("pt-BR", { pagina: "home" })).toBe("/en");
    expect(noOutroIdioma("en", { pagina: "home" })).toBe("/pt-BR");
  });
});
