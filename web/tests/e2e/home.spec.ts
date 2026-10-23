import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

/**
 * Criterio de verificacao da tarefa 002-T15: AC-07.
 *
 * A contagem exibida e comparada com a que a API devolve, e nao com um numero
 * escrito no teste: numero fixo aqui viraria mais uma copia do seed, e quebraria
 * junto com ele por um motivo que nao tem nada a ver com a home.
 */
test("a home lista as categorias com a contagem da API @smoke", async ({ page, request }) => {
  const resposta = await page.goto("/pt-BR");
  expect(resposta?.status()).toBe(200);

  await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR");
  await expect(page.getByTestId(TESTID.homeTitulo)).toBeVisible();

  const daApi = await (await request.get("http://localhost:8080/api/v1/categories")).json();
  const filhasNaApi = daApi.items.flatMap((raiz: { children: unknown[] }) => raiz.children);

  await expect(page.getByTestId(TESTID.categoriaCard)).toHaveCount(filhasNaApi.length);
});

test("cada card leva para a categoria dele, com o slug do idioma", async ({ page }) => {
  await page.goto("/pt-BR");

  const primeiro = page.getByTestId(TESTID.categoriaCard).first();
  const slug = await primeiro.getAttribute("data-slug");

  await expect(primeiro).toHaveAttribute("href", `/pt-BR/categorias/${slug}`);
});

test("em ingles, os slugs e as contagens sao os da versao inglesa", async ({ page, request }) => {
  await page.goto("/en");

  const daApi = await (
    await request.get("http://localhost:8080/api/v1/categories", {
      headers: { "Accept-Language": "en" },
    })
  ).json();

  const slugsNaApi = daApi.items
    .flatMap((raiz: { children: { slug: string }[] }) => raiz.children)
    .map((filha: { slug: string }) => filha.slug);

  const slugsNaPagina = await page
    .getByTestId(TESTID.categoriaCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  expect(slugsNaPagina).toEqual(slugsNaApi);
});

test("a busca e um formulario GET, que funciona sem JavaScript", async ({ page }) => {
  await page.goto("/pt-BR");

  const formulario = page.getByTestId(TESTID.buscaFormulario);
  await expect(formulario).toHaveAttribute("method", /get/i);
  await expect(formulario).toHaveAttribute("action", "/pt-BR/buscar");
  await expect(page.getByTestId(TESTID.buscaCampo)).toHaveAttribute("name", "q");
});
