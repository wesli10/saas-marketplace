import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

const CATEGORIA = "/pt-BR/categorias/agentes-e-workflows";

/**
 * Criterio de verificacao da tarefa 002-T18: AC-09.
 *
 * Roda no projeto `chromium-no-js`, com `javaScriptEnabled: false`. Nao e o
 * mesmo que "usuario sem JavaScript" - o HTML ainda e o que o Next serviu -,
 * mas cobre o que importa: o formulario funcionar sem handler nenhum (plano,
 * risco 3).
 */
test("navegar sem JavaScript @no-js", async ({ page }) => {
  const resposta = await page.goto("/pt-BR");
  expect(resposta?.status()).toBe(200);

  await page.getByTestId(TESTID.categoriaCard).first().click();

  await expect(page).toHaveURL(/\/pt-BR\/categorias\//);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("filtrar sem JavaScript @no-js", async ({ page }) => {
  await page.goto(CATEGORIA);
  const antes = await page.getByTestId(TESTID.produtoCard).count();

  // O botao existe justamente porque nao ha JavaScript para submeter sozinho.
  await expect(page.getByTestId(TESTID.filtroAplicar)).toBeVisible();
  await page.getByTestId(TESTID.filtroPlanoGratuito).check();
  await page.getByTestId(TESTID.filtroAplicar).click();

  await expect(page).toHaveURL(/has_free_plan=true/);
  const depois = await page.getByTestId(TESTID.produtoCard).count();
  expect(depois).toBeLessThan(antes);
  expect(depois).toBeGreaterThan(0);
});

test("os controles refletem a URL sem JavaScript @no-js", async ({ page }) => {
  await page.goto(`${CATEGORIA}?has_free_plan=true`);

  // Sem JS nao ha hidratacao: se isto passa, o estado veio do HTML do servidor.
  await expect(page.getByTestId(TESTID.filtroPlanoGratuito)).toBeChecked();
});

test("limpar filtros sem JavaScript @no-js", async ({ page }) => {
  await page.goto(`${CATEGORIA}?has_free_plan=true`);

  await page.getByTestId(TESTID.filtroLimpar).click();

  await expect(page).toHaveURL(CATEGORIA);
});

test("a busca da home funciona sem JavaScript @no-js", async ({ page }) => {
  await page.goto("/pt-BR");

  await page.getByTestId(TESTID.buscaCampo).fill("chatbot");
  await page.getByTestId(TESTID.buscaEnviar).click();

  await expect(page).toHaveURL(/\/pt-BR\/buscar\?q=chatbot/);
});

/**
 * A outra metade do AC-09: "aplicar um filtro **e paginar**". Chegou junto com
 * a paginacao, no 002-T19 - antes dela nao havia o que paginar.
 */
test("paginar sem JavaScript @no-js", async ({ page }) => {
  // A raiz e a unica categoria com mais produtos do que cabe numa pagina.
  await page.goto("/pt-BR/categorias/ia-e-automacao-para-pmes");

  await page.getByTestId(TESTID.paginacaoProxima).click();

  await expect(page).toHaveURL(/page=1/);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});
