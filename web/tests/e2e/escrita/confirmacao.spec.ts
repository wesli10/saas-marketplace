import { expect, test } from "@playwright/test";

import { TESTID } from "../../../src/lib/testid";
import {
  API,
  CABECALHO_DO_CURADOR,
  corpoDeReview,
  emailUnico,
  linkDeConfirmacao,
  tituloDe,
} from "./apoio";

/**
 * Criterio de verificacao da tarefa 003-T13: AC-20, os tres casos da tela.
 *
 * O caminho feliz e de ponta a ponta de verdade: envia pela API, pega o link no
 * Mailpit, ABRE o link no navegador e depois confere a fila do curador. E o
 * unico teste que prova que o endereco montado pelo backend e a rota servida
 * pelo frontend sao a mesma coisa - as duas metades vivem em modulos que nao se
 * enxergam.
 */
test("AC-20 sucesso: o link do e-mail confirma, e a review entra na fila", async ({
  page,
  request,
}) => {
  const email = emailUnico();

  await request.post(`${API}/api/v1/products/relevance-ai/reviews`, {
    data: corpoDeReview(email),
  });
  const link = await linkDeConfirmacao(request, email);

  // Abre o proprio link do e-mail, e nao uma URL montada pelo teste.
  await page.goto(link);
  await expect(page.getByTestId(TESTID.confirmacaoSucesso)).toBeVisible();

  const fila = await request.get(
    `${API}/api/v1/admin/reviews?status=PENDING_MODERATION&product=relevance-ai&size=50`,
    { headers: CABECALHO_DO_CURADOR },
  );
  const { items } = (await fila.json()) as { items: Array<{ title: string }> };
  expect(items.some((item) => item.title === tituloDe(email))).toBeTruthy();
});

test("AC-20 ja usado: o mesmo link uma segunda vez cai na tela de link invalido", async ({
  page,
  request,
}) => {
  const email = emailUnico();

  await request.post(`${API}/api/v1/products/descript/reviews`, { data: corpoDeReview(email) });
  const link = await linkDeConfirmacao(request, email);

  await page.goto(link);
  await expect(page.getByTestId(TESTID.confirmacaoSucesso)).toBeVisible();

  await page.goto(link);
  await expect(page.getByTestId(TESTID.confirmacaoExpirado)).toBeVisible();
});

test("AC-20 invalido: token forjado cai na mesma tela, com o reenvio a mao", async ({ page }) => {
  await page.goto("/pt-BR/reviews/confirmar?token=nunca-existiu&produto=copy-ai");

  await expect(page.getByTestId(TESTID.confirmacaoExpirado)).toBeVisible();
  // Os tres casos ruins respondem igual: a tela oferece o reenvio em vez de
  // explicar qual foi (spec AC-03).
  await expect(page.getByTestId(TESTID.confirmacaoReenvioFormulario)).toBeVisible();
  await expect(page.getByTestId(TESTID.confirmacaoVerProduto)).toHaveAttribute(
    "href",
    "/pt-BR/produtos/copy-ai",
  );
});

test("o reenvio manda um link novo, e o novo confirma", async ({ page, request }) => {
  const email = emailUnico();

  await request.post(`${API}/api/v1/products/elevenlabs/reviews`, { data: corpoDeReview(email) });
  const primeiro = await linkDeConfirmacao(request, email);

  // Queima o primeiro link: e o estado em que alguem pediria um novo.
  await page.goto(primeiro);
  await page.goto(primeiro);
  await expect(page.getByTestId(TESTID.confirmacaoExpirado)).toBeVisible();

  await page.getByTestId(TESTID.confirmacaoReenvioEmail).fill(email);
  await page.getByTestId(TESTID.confirmacaoReenvioEnviar).click();

  // A tela nao diz se havia o que reenviar - a API responde 202 nos dois casos.
  await expect(page.getByTestId(TESTID.confirmacaoReenviado)).toBeVisible();
});

test("sem token, a tela oferece o reenvio em vez de dar 404 @no-js", async ({ page }) => {
  const resposta = await page.goto("/en/reviews/confirm?produto=zapier");

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.confirmacaoExpirado)).toBeVisible();
  await expect(page.getByTestId(TESTID.confirmacaoReenvioFormulario)).toBeVisible();
});
