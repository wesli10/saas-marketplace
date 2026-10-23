import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

// `zapierr` casa por trigram (um caractere a mais em "zapier"); `zapier` casa
// por full-text. Os dois vem do seed do 001, e nao de dado inventado.
const TERMO_EXATO = "zapier";
const TERMO_COM_ERRO = "zapierr";

/** Criterio de verificacao da tarefa 002-T20: AC-11. */
test("erro de digitacao mostra o aviso de resultado aproximado", async ({ page }) => {
  await page.goto(`/pt-BR/buscar?q=${TERMO_COM_ERRO}`);

  await expect(page.getByTestId(TESTID.buscaAproximada)).toBeVisible();
  await expect(page.getByTestId(TESTID.buscaAproximada)).toContainText(TERMO_COM_ERRO);
  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(1);
});

test("busca exata nao mostra o aviso", async ({ page }) => {
  await page.goto(`/pt-BR/buscar?q=${TERMO_EXATO}`);

  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
  await expect(page.getByTestId(TESTID.buscaAproximada)).toHaveCount(0);
});

test("o aviso sai no idioma da rota", async ({ page }) => {
  await page.goto(`/en/search?q=${TERMO_COM_ERRO}`);

  await expect(page.getByTestId(TESTID.buscaAproximada)).toContainText("Nothing exact");
});

test("a busca reusa a lista, com filtros e paginacao", async ({ page }) => {
  await page.goto("/pt-BR/buscar?q=whatsapp&size=2");

  await expect(page.getByTestId(TESTID.filtroPainel)).toBeVisible();
  await expect(page.getByTestId(TESTID.paginacao)).toBeVisible();

  await page.getByTestId(TESTID.paginacaoProxima).click();

  // Paginar dentro da busca nao pode perder o termo.
  await expect(page).toHaveURL(/q=whatsapp/);
  await expect(page).toHaveURL(/page=1/);
});

test("filtrar dentro da busca preserva o termo", async ({ page }) => {
  await page.goto(`/pt-BR/buscar?q=whatsapp`);

  await page.getByTestId(TESTID.filtroPlanoGratuito).check();

  await expect(page).toHaveURL(/q=whatsapp/);
  await expect(page).toHaveURL(/has_free_plan=true/);
});

test("o campo volta preenchido com o termo buscado", async ({ page }) => {
  await page.goto(`/pt-BR/buscar?q=${TERMO_EXATO}`);

  // Refinar e trocar uma palavra, e nao digitar tudo de novo.
  await expect(page.getByTestId(TESTID.buscaCampo)).toHaveValue(TERMO_EXATO);
});

test("busca sem termo nao lista o catalogo inteiro", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/buscar");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.buscaSemTermo)).toBeVisible();
  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(0);
});

test("busca sem resultado nenhum responde 200, e nao erro", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/buscar?q=xyzabc");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.listaVazia)).toBeVisible();
});
