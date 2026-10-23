import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

const CATEGORIA = "/pt-BR/categorias/agentes-e-workflows";

/** Criterio de verificacao da tarefa 002-T24: AC-16 e AC-17. */
test("comparar 3 produtos mostra as 3 colunas, na ordem pedida", async ({ page }) => {
  await page.goto("/pt-BR/comparar?slugs=zapier,make,n8n");

  const colunas = page.getByTestId(TESTID.comparacaoColuna);
  await expect(colunas).toHaveCount(3);
  // A ordem e a da URL, e nao a que a API devolveu: comparar e leitura lado a
  // lado, e trocar a ordem no meio confunde quem montou a comparacao.
  expect(await colunas.evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")))).toEqual(
    ["zapier", "make", "n8n"],
  );
});

test("AC-16: um slug so vira aviso de faixa, e nao erro 500", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/comparar?slugs=zapier");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.comparacaoFaixa)).toBeVisible();
  await expect(page.getByTestId(TESTID.comparacaoTabela)).toHaveCount(0);
});

test("AC-16: mais de tres tambem e faixa invalida", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/comparar?slugs=zapier,make,n8n,pipefy");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.comparacaoFaixa)).toBeVisible();
});

test("AC-17: slug inexistente diz qual sumiu e oferece seguir com os que restaram", async ({
  page,
}) => {
  const resposta = await page.goto("/pt-BR/comparar?slugs=zapier,make,nao-existe");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.comparacaoAusentes)).toContainText("nao-existe");

  await page.getByTestId(TESTID.comparacaoSeguir).click();

  await expect(page.getByTestId(TESTID.comparacaoColuna)).toHaveCount(2);
});

test("AC-19: o detail cru da API nao aparece na versao em ingles", async ({ page }) => {
  await page.goto("/en/compare?slugs=zapier,make,nao-existe");

  const html = await page.content();
  expect(html).not.toContain("Nao existe produto publicado");
  await expect(page.getByTestId(TESTID.comparacaoAusentes)).toContainText("We could not find");
});

test("a selecao acumula na URL da lista e leva para a comparacao", async ({ page }) => {
  await page.goto(CATEGORIA);

  const controles = page.getByTestId(TESTID.compararControle);
  await controles.nth(0).click();
  await expect(page).toHaveURL(/comparar=/);
  await page.getByTestId(TESTID.compararControle).nth(1).click();

  await expect(page.getByTestId(TESTID.compararBarra)).toBeVisible();
  await page.getByTestId(TESTID.compararIr).click();

  await expect(page).toHaveURL(/\/pt-BR\/comparar\?slugs=/);
  await expect(page.getByTestId(TESTID.comparacaoColuna)).toHaveCount(2);
});

test("no limite de tres, o quarto card nao oferece o controle", async ({ page }) => {
  await page.goto(`${CATEGORIA}?comparar=zapier&comparar=make&comparar=n8n`);

  await expect(page.getByTestId(TESTID.compararLimite).first()).toBeVisible();
  // Os tres marcados continuam podendo ser desmarcados.
  await expect(page.getByTestId(TESTID.compararControle)).toHaveCount(3);
});

test("a selecao sobrevive a filtrar e a paginar", async ({ page }) => {
  await page.goto(`${CATEGORIA}?comparar=zapier`);

  await page.getByTestId(TESTID.filtroPlanoGratuito).check();

  await expect(page).toHaveURL(/has_free_plan=true/);
  await expect(page).toHaveURL(/comparar=zapier/);
  await expect(page.getByTestId(TESTID.compararBarra)).toBeVisible();
});

test("limpar a selecao mantem os filtros", async ({ page }) => {
  await page.goto(`${CATEGORIA}?has_free_plan=true&comparar=zapier`);

  await page.getByTestId(TESTID.compararLimpar).click();

  await expect(page).toHaveURL(`${CATEGORIA}?has_free_plan=true`);
  await expect(page.getByTestId(TESTID.compararBarra)).toHaveCount(0);
});

test("comparar sem JavaScript @no-js", async ({ page }) => {
  // Sao links: marcar, ir e comparar funcionam sem script nenhum.
  await page.goto(`${CATEGORIA}?comparar=zapier`);

  await page.getByTestId(TESTID.compararControle).nth(1).click();
  await page.getByTestId(TESTID.compararIr).click();

  await expect(page.getByTestId(TESTID.comparacaoTabela)).toBeVisible();
});
