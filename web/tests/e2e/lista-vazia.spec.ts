import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

const CATEGORIA = "/pt-BR/categorias/agentes-e-workflows";

/**
 * Criterio de verificacao da tarefa 002-T21: AC-12.
 *
 * A combinacao abaixo existe no seed e nao devolve nada, mas cada criterio
 * sozinho devolve: `has_free_plan` traz 3 produtos nesta categoria, e e o
 * `price_max=1` que zera. Assim o teste de "remover um criterio" prova que
 * saiu **um**, e nao os dois.
 */
const SEM_RESULTADO = `${CATEGORIA}?has_free_plan=true&price_max=1`;

test("zero resultado responde 200 e diz o que foi filtrado", async ({ page }) => {
  const resposta = await page.goto(SEM_RESULTADO);

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.listaVazia)).toBeVisible();
  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(0);
  await expect(page.getByTestId(TESTID.vazioRemoverFiltro)).toHaveCount(2);
});

test("remover um filtro tira so aquele criterio", async ({ page }) => {
  await page.goto(SEM_RESULTADO);

  await page
    .locator(`[data-testid="${TESTID.vazioRemoverFiltro}"][data-campo="price_max"]`)
    .click();

  await expect(page).toHaveURL(/has_free_plan=true/);
  await expect(page).not.toHaveURL(/price_max/);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("limpar tudo devolve a lista sem filtro nenhum", async ({ page }) => {
  await page.goto(SEM_RESULTADO);

  await page.getByTestId(TESTID.vazioLimparTudo).click();

  await expect(page).toHaveURL(CATEGORIA);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("o vazio oferece as categorias como alternativa", async ({ page }) => {
  await page.goto(SEM_RESULTADO);

  const alternativas = page.getByTestId(TESTID.vazioCategoria);
  await expect(alternativas.first()).toBeVisible();

  await alternativas.first().click();

  await expect(page).toHaveURL(/\/pt-BR\/categorias\//);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("busca sem resultado tambem oferece saida, no idioma da rota", async ({ page }) => {
  const resposta = await page.goto("/en/search?q=xyzabc");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.listaVazia)).toContainText("No products");
  await expect(page.getByTestId(TESTID.vazioRemoverFiltro)).toHaveCount(1);
});

test("remover filtros sem JavaScript @no-js", async ({ page }) => {
  // Sao links, entao a saida do vazio nao depende de script nenhum.
  await page.goto(SEM_RESULTADO);

  await page.getByTestId(TESTID.vazioLimparTudo).click();

  await expect(page).toHaveURL(CATEGORIA);
});
