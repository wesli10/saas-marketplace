import { describe, expect, it } from "vitest";

import { rotaBusca, rotaCategoria, rotaComparacao, rotaHome, rotaProduto } from "@/src/lib/routes";

/** Criterio de verificacao da tarefa 002-T11: os cinco pares, nos dois idiomas. */
describe("mapa de rotas", () => {
  it("home", () => {
    expect(rotaHome("pt-BR")).toBe("/pt-BR");
    expect(rotaHome("en")).toBe("/en");
  });

  it("categoria traduz o segmento e o slug vem de fora", () => {
    expect(rotaCategoria("pt-BR", "atendimento-e-chatbots")).toBe(
      "/pt-BR/categorias/atendimento-e-chatbots",
    );
    expect(rotaCategoria("en", "customer-support-and-chatbots")).toBe(
      "/en/categories/customer-support-and-chatbots",
    );
  });

  it("produto traduz so o segmento: o slug e o mesmo nos dois idiomas", () => {
    // Spec 001 secao 4.2 - nome de produto nao se traduz.
    expect(rotaProduto("pt-BR", "zapier")).toBe("/pt-BR/produtos/zapier");
    expect(rotaProduto("en", "zapier")).toBe("/en/products/zapier");
  });

  it("busca, com e sem query", () => {
    expect(rotaBusca("pt-BR")).toBe("/pt-BR/buscar");
    expect(rotaBusca("en")).toBe("/en/search");
    expect(rotaBusca("pt-BR", new URLSearchParams({ q: "chatbot" }))).toBe(
      "/pt-BR/buscar?q=chatbot",
    );
    // Query vazia nao vira "?" pendurado na URL.
    expect(rotaBusca("en", "")).toBe("/en/search");
  });

  it("comparacao acumula os slugs na URL", () => {
    expect(rotaComparacao("pt-BR", ["zapier", "make"])).toBe("/pt-BR/comparar?slugs=zapier,make");
    expect(rotaComparacao("en", ["zapier", "make", "n8n"])).toBe(
      "/en/compare?slugs=zapier,make,n8n",
    );
    // Sem slug nenhum, a pagina ainda existe: ela mostra o aviso de faixa.
    expect(rotaComparacao("en", [])).toBe("/en/compare");
  });
});
