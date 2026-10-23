import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

/**
 * Criterio de verificacao da tarefa 003-T14: AC-21a e AC-21b.
 *
 * Suite compartilhada: a politica e leitura pura, e chegar ate ela pelos dois
 * links tambem. Nenhum destes casos escreve.
 */
test("a politica responde nos dois idiomas, com a rota traduzida", async ({ page }) => {
  const emPortugues = await page.goto("/pt-BR/politica-de-reviews");
  expect(emPortugues?.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR");
  await expect(page.getByTestId(TESTID.politicaTitulo)).toBeVisible();

  const emIngles = await page.goto("/en/review-policy");
  expect(emIngles?.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByTestId(TESTID.politicaTitulo)).toBeVisible();
});

test("AC-21b: a politica publica o endereco de contestacao e o limite de remocao", async ({
  page,
}) => {
  await page.goto("/pt-BR/politica-de-reviews");

  const endereco = page.getByTestId(TESTID.politicaContestacaoEndereco);
  await expect(endereco).toBeVisible();
  // `mailto:` de verdade: contestacao que depende de alguem copiar um texto da
  // tela e uma contestacao a menos.
  await expect(endereco).toHaveAttribute("href", /^mailto:.+@.+\..+/);

  // O prazo esta escrito junto do endereco, e nao numa promessa vaga.
  await expect(page.getByTestId(TESTID.politicaContestacao)).toHaveText(/\d+\s+dias/);

  // A regra que protege quem escreveu: nao sai por pedido de quem foi avaliado
  // quando nao viola a politica (spec secao 7.1).
  await expect(page.getByTestId(TESTID.politicaLimiteDeRemocao)).toBeVisible();

  // E o que e removido esta listado, e nao subentendido.
  expect(await page.getByTestId(TESTID.politicaRemovido).count()).toBeGreaterThan(3);
});

test("AC-21a: a politica entra no sitemap, nos dois idiomas", async ({ request }) => {
  const sitemap = await (await request.get("/sitemap.xml")).text();

  expect(sitemap).toContain("/pt-BR/politica-de-reviews");
  expect(sitemap).toContain("/en/review-policy");
  // O formulario e a confirmacao ficam de fora: sao transacionais, e a
  // confirmacao ainda carrega token na URL.
  expect(sitemap).not.toContain("/avaliar");
  expect(sitemap).not.toContain("/reviews/confirmar");
});

test("AC-21a: os dois links levam ate ela - o da ficha e o do formulario", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");
  await page.getByTestId(TESTID.reviewsPolitica).click();
  await expect(page).toHaveURL(/\/pt-BR\/politica-de-reviews$/);
  await expect(page.getByTestId(TESTID.politicaTitulo)).toBeVisible();

  await page.goto("/en/products/zapier/review");
  await page.getByRole("link", { name: /how reviews work/i }).click();
  await expect(page).toHaveURL(/\/en\/review-policy$/);
  await expect(page.getByTestId(TESTID.politicaTitulo)).toBeVisible();
});

test("a politica e alcancavel sem JavaScript @no-js", async ({ page }) => {
  await page.goto("/pt-BR/produtos/zapier");
  await page.getByTestId(TESTID.reviewsPolitica).click();

  await expect(page.getByTestId(TESTID.politicaTitulo)).toBeVisible();
  await expect(page.getByTestId(TESTID.politicaContestacaoEndereco)).toBeVisible();
});
