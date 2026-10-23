import { describe, expect, it } from "vitest";

import type { ProdutoDetalhe } from "@/src/lib/api/catalog";
import { softwareApplication, trilhaDaCategoria, trilhaDoProduto } from "@/src/lib/seo/jsonld";

/** Criterio de verificacao da tarefa 002-T25: AC-15. */
const PRODUTO: ProdutoDetalhe = {
  slug: "acme",
  name: "Acme",
  tagline: "Automation for small teams",
  description: "Longer description.",
  website_url: "https://acme.com",
  category: { slug: "agents-and-workflows", name: "Agents and workflows" },
  price_min: 19.99,
  price_currency: "USD",
};

describe("JSON-LD da ficha", () => {
  it("AC-15: traz nome, categoria e preco quando ha preco", () => {
    const dados = softwareApplication(PRODUTO, "en");

    expect(dados["@type"]).toBe("SoftwareApplication");
    expect(dados.name).toBe("Acme");
    expect(dados.applicationCategory).toBe("Agents and workflows");
    expect(dados.offers).toEqual({
      "@type": "Offer",
      price: "19.99",
      priceCurrency: "USD",
      url: "https://acme.com",
    });
  });

  it("sem preco publicado, nao existe bloco de oferta", () => {
    // Preco "sob consulta" e diferente de preco zero: anunciar um preco que nao
    // existe e uma afirmacao falsa sobre o produto.
    const dados = softwareApplication(
      { ...PRODUTO, price_min: undefined, price_currency: undefined },
      "en",
    );

    expect(dados.offers).toBeUndefined();
    expect(dados.name).toBe("Acme");
  });

  it("a url do produto e absoluta e no idioma da pagina", () => {
    expect(softwareApplication(PRODUTO, "pt-BR").url).toBe(
      "http://localhost:3000/pt-BR/produtos/acme",
    );
    expect(softwareApplication(PRODUTO, "en").url).toBe("http://localhost:3000/en/products/acme");
  });

  it("campo ausente nao vira campo vazio", () => {
    const dados = softwareApplication({ slug: "so-nome", name: "So nome" }, "en");

    expect(dados.description).toBeUndefined();
    expect(dados.applicationCategory).toBeUndefined();
    expect(dados.sameAs).toBeUndefined();
  });
});

describe("trilha de navegacao", () => {
  it("do produto: home, categoria e produto, contando a partir de 1", () => {
    const trilha = trilhaDoProduto(PRODUTO, "pt-BR", "Marketplace");

    expect(trilha["@type"]).toBe("BreadcrumbList");
    expect(trilha.itemListElement).toEqual([
      {
        "@type": "ListItem",
        position: 1,
        name: "Marketplace",
        item: "http://localhost:3000/pt-BR",
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "Agents and workflows",
        item: "http://localhost:3000/pt-BR/categorias/agents-and-workflows",
      },
      {
        "@type": "ListItem",
        position: 3,
        name: "Acme",
        item: "http://localhost:3000/pt-BR/produtos/acme",
      },
    ]);
  });

  it("produto sem categoria pula o degrau, em vez de emitir um vazio", () => {
    const trilha = trilhaDoProduto({ ...PRODUTO, category: undefined }, "en", "Marketplace");

    expect((trilha.itemListElement as unknown[]).length).toBe(2);
  });

  it("da categoria: home e categoria", () => {
    const trilha = trilhaDaCategoria(
      { slug: "agentes-e-workflows", name: "Agentes e workflows" },
      "pt-BR",
      "Marketplace",
    );

    expect((trilha.itemListElement as { name: string }[]).map((i) => i.name)).toEqual([
      "Marketplace",
      "Agentes e workflows",
    ]);
  });
});
