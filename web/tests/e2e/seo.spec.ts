import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T25: AC-06 e AC-15.
 *
 * O `SITE_URL` do compose e `http://localhost:3000`, e e ele que aparece nas
 * canonicas - por isso as assercoes usam esse prefixo.
 */
const SITE = "http://localhost:3000";

const canonica = (page: import("@playwright/test").Page) =>
  page.locator('link[rel="canonical"]').getAttribute("href");

const alternativa = (page: import("@playwright/test").Page, idioma: string) =>
  page.locator(`link[rel="alternate"][hreflang="${idioma}"]`).getAttribute("href");

test("AC-06: a canonica e absoluta e aponta para a propria pagina", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");

  expect(await canonica(page)).toBe(`${SITE}/pt-BR/produtos/zapier`);
});

test("AC-06: o hreflang e reciproco nas duas versoes", async ({ page }) => {
  // Reciprocidade e o que o rastreador exige para tratar o par como o mesmo
  // conteudo em dois idiomas: uma seta so ele ignora.
  await page.goto("/pt-BR/produtos/zapier");
  expect(await alternativa(page, "pt-BR")).toBe(`${SITE}/pt-BR/produtos/zapier`);
  expect(await alternativa(page, "en")).toBe(`${SITE}/en/products/zapier`);
  expect(await alternativa(page, "x-default")).toBe(`${SITE}/pt-BR/produtos/zapier`);

  await page.goto("/en/products/zapier");
  expect(await alternativa(page, "pt-BR")).toBe(`${SITE}/pt-BR/produtos/zapier`);
  expect(await alternativa(page, "en")).toBe(`${SITE}/en/products/zapier`);
});

test("AC-06: na categoria, o par vem do alternates e nao do slug traduzido a mao", async ({
  page,
}) => {
  await page.goto("/pt-BR/categorias/agentes-e-workflows");

  expect(await canonica(page)).toBe(`${SITE}/pt-BR/categorias/agentes-e-workflows`);
  expect(await alternativa(page, "en")).toBe(`${SITE}/en/categories/agents-and-workflows`);
});

test("AC-06: a home tambem traz canonica e o par", async ({ page }) => {
  await page.goto("/en");

  expect(await canonica(page)).toBe(`${SITE}/en`);
  expect(await alternativa(page, "pt-BR")).toBe(`${SITE}/pt-BR`);
  expect(await alternativa(page, "x-default")).toBe(`${SITE}/pt-BR`);
});

test("a canonica da busca nao carrega a query", async ({ page }) => {
  // Cada termo digitado viraria uma URL nova sem conteudo proprio.
  await page.goto("/pt-BR/buscar?q=zapier");

  expect(await canonica(page)).toBe(`${SITE}/pt-BR/buscar`);
});

test("a comparacao fica fora do indice, mas os links continuam sendo seguidos", async ({
  page,
}) => {
  await page.goto("/pt-BR/comparar?slugs=zapier,make");

  const robots = await page.locator('meta[name="robots"]').getAttribute("content");
  expect(robots).toContain("noindex");
  expect(robots).toContain("follow");
});

test("o titulo da pagina sai do conteudo, e no idioma da rota", async ({ page }) => {
  await page.goto("/en/products/zapier");
  await expect(page).toHaveTitle(/Zapier/);

  await page.goto("/en/categories/agents-and-workflows");
  await expect(page).toHaveTitle(/Agents and workflows/);
});

test("AC-15: a ficha traz SoftwareApplication com nome, categoria e preco", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");

  const blocos = await page.locator('script[type="application/ld+json"]').allTextContents();
  const dados = blocos.flatMap((bloco) => {
    const conteudo = JSON.parse(bloco);
    return Array.isArray(conteudo) ? conteudo : [conteudo];
  });

  const aplicacao = dados.find((d) => d["@type"] === "SoftwareApplication");
  expect(aplicacao).toBeDefined();
  expect(aplicacao.name).toBe("Zapier");
  expect(aplicacao.applicationCategory).toBeTruthy();
  expect(aplicacao.offers.priceCurrency).toBeTruthy();

  const trilha = dados.find((d) => d["@type"] === "BreadcrumbList");
  expect(trilha.itemListElement).toHaveLength(3);
});

test("a categoria traz a trilha, e nao o SoftwareApplication", async ({ page }) => {
  await page.goto("/pt-BR/categorias/agentes-e-workflows");

  const blocos = await page.locator('script[type="application/ld+json"]').allTextContents();
  const tipos = blocos.map((bloco) => JSON.parse(bloco)["@type"]);

  expect(tipos).toContain("BreadcrumbList");
  expect(tipos).not.toContain("SoftwareApplication");
});
