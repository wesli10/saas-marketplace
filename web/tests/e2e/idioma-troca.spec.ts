import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

/**
 * Criterio de verificacao da tarefa 002-T23: AC-05. Quinto caso da smoke.
 *
 * O que o AC cobra e o oposto do comportamento mais facil de implementar:
 * mandar todo mundo para a home ao trocar de idioma funcionaria e seria
 * inutil para quem esta lendo uma ficha.
 */
test("trocar o idioma na ficha leva ao mesmo produto @smoke", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");

  await page.getByTestId(TESTID.seletorIdioma).click();

  await expect(page).toHaveURL("/en/products/zapier");
  await expect(page.getByTestId(TESTID.fichaTitulo)).toHaveText("Zapier");
});

test("na categoria, o slug do outro idioma vem da API", async ({ page }) => {
  await page.goto("/pt-BR/categorias/agentes-e-workflows");

  await page.getByTestId(TESTID.seletorIdioma).click();

  // O slug MUDA com o idioma: adivinhar daria 404 silencioso.
  await expect(page).toHaveURL("/en/categories/agents-and-workflows");
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("trocar de idioma na categoria preserva os filtros", async ({ page }) => {
  await page.goto("/pt-BR/categorias/agentes-e-workflows?has_free_plan=true");
  const antes = await page.getByTestId(TESTID.produtoCard).count();

  await page.getByTestId(TESTID.seletorIdioma).click();

  await expect(page).toHaveURL(/\/en\/categories\/agents-and-workflows\?has_free_plan=true/);
  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(antes);
});

test("trocar de idioma na busca continua a mesma busca", async ({ page }) => {
  await page.goto("/pt-BR/buscar?q=zapier");

  await page.getByTestId(TESTID.seletorIdioma).click();

  await expect(page).toHaveURL(/\/en\/search\?q=zapier/);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("a volta funciona: en para pt-BR no mesmo produto", async ({ page }) => {
  await page.goto("/en/products/zapier");

  await page.getByTestId(TESTID.seletorIdioma).click();

  await expect(page).toHaveURL("/pt-BR/produtos/zapier");
});

test("o link anuncia o idioma de destino", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");

  // `hreflang` e para o robo; o `data-locale` e para o teste nao depender do
  // texto traduzido do link.
  await expect(page.getByTestId(TESTID.seletorIdioma)).toHaveAttribute("hreflang", "en");
  await expect(page.getByTestId(TESTID.seletorIdioma)).toHaveAttribute("data-locale", "en");
});

test("trocar de idioma sem JavaScript @no-js", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");

  await page.getByTestId(TESTID.seletorIdioma).click();

  await expect(page).toHaveURL("/en/products/zapier");
});
