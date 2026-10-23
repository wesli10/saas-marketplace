import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T14: AC-18 e AC-19.
 *
 * Roda contra `web-sem-api`, uma instancia propria com a API apontada para um
 * endereco morto. Nao ha `docker stop` aqui de proposito: derrubar o `app`
 * compartilhado no meio de uma execucao paralela mataria os outros workers
 * (plano DT-10.3).
 */
test("com a API fora, a pagina de erro aparece e o status e 5xx", async ({ page }) => {
  const resposta = await page.goto("/pt-BR");

  expect(resposta?.status()).toBeGreaterThanOrEqual(500);
  await expect(page.getByTestId("pagina-de-erro")).toBeVisible();
});

test("a pagina de erro respeita o idioma da rota", async ({ page }) => {
  await page.goto("/en");

  await expect(page.getByTestId("pagina-de-erro")).toBeVisible();
  // Assercao sobre o texto: e o proprio idioma que esta sendo verificado.
  await expect(page.getByText("Something went wrong")).toBeVisible();
});

test("nada de stack trace nem detalhe cru da API no HTML servido", async ({ page }) => {
  await page.goto("/en");
  const html = await page.content();

  expect(html).not.toContain("ApiIndisponivelError");
  expect(html).not.toContain("at async");
  // O `detail` da API viria em portugues numa pagina em ingles (AC-19).
  expect(html).not.toContain("http://127.0.0.1:9");
});
