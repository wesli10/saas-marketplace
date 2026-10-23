import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T14: AC-13.
 *
 * O que importa e o **status**, e nao so a aparencia: pagina de "nao
 * encontrado" servida com 200 faz o rastreador indexar o vazio.
 */
test("slug que nao existe responde 404 de verdade @smoke", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/produtos/nao-existe-mesmo");

  expect(resposta?.status()).toBe(404);
  await expect(page.getByTestId("pagina-nao-encontrada")).toBeVisible();
});

test("a pagina de 404 sai no idioma da rota", async ({ page }) => {
  await page.goto("/en/products/nao-existe-mesmo");

  await expect(page.getByTestId("link-home")).toHaveAttribute("href", "/en");
});
