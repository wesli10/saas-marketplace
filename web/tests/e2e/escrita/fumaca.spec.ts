import { expect, test } from "@playwright/test";

import { TESTID } from "../../../src/lib/testid";
import {
  API,
  CABECALHO_DO_CURADOR,
  corpoDeReview,
  emailUnico,
  linkDeConfirmacao,
  tokenDe,
} from "./apoio";

/**
 * Criterio de verificacao da tarefa 003-T10: a instancia de escrita esta de pe, o
 * Mailpit entrega, e a fila do curador enxerga o que foi escrito (AC-22, AC-23).
 *
 * E um caso de infraestrutura, e nao de tela: o formulario so existe a partir da
 * T12. O que ele prova e que o caminho inteiro - POST, e-mail, confirmacao, fila -
 * funciona no ambiente onde as telas vao ser testadas.
 *
 * Cada teste usa um e-mail proprio (constituicao secao 6): o indice unico da V7 e
 * por (produto, e-mail), e reaproveitar um endereco faria o segundo envio nao
 * criar nada e o teste falhar por um motivo que nao e o dele.
 */
test("uma review enviada aparece na fila do curador depois de confirmada", async ({ request }) => {
  const email = emailUnico();

  const envio = await request.post(`${API}/api/v1/products/zapier/reviews`, {
    data: corpoDeReview(email),
  });
  expect(envio.status()).toBe(202);

  const link = await linkDeConfirmacao(request, email);
  expect(link).toContain("/reviews/confirmar?token=");

  const confirmacao = await request.post(`${API}/api/v1/reviews/confirm`, {
    data: { token: tokenDe(link) },
  });
  expect(confirmacao.status()).toBe(200);

  const fila = await request.get(
    `${API}/api/v1/admin/reviews?status=PENDING_MODERATION&product=zapier`,
    {
      headers: CABECALHO_DO_CURADOR,
    },
  );
  expect(fila.ok()).toBeTruthy();

  const { items } = (await fila.json()) as {
    items: Array<{ author_name: string; author_domain: string }>;
  };
  expect(items.some((item) => item.author_domain === "teste.local")).toBeTruthy();
});

test("a review confirmada e ainda nao aprovada nao aparece na leitura publica", async ({
  request,
}) => {
  const email = emailUnico();

  const antes = await request.get(`${API}/api/v1/products/zapier/reviews`);
  const { total: totalAntes } = (await antes.json()) as { total: number };

  await request.post(`${API}/api/v1/products/zapier/reviews`, { data: corpoDeReview(email) });
  const link = await linkDeConfirmacao(request, email);
  await request.post(`${API}/api/v1/reviews/confirm`, { data: { token: tokenDe(link) } });

  const depois = await request.get(`${API}/api/v1/products/zapier/reviews`);
  const { total: totalDepois } = (await depois.json()) as { total: number };

  // Confirmada, mas nao aprovada: continua invisivel para quem le (AC-02).
  expect(totalDepois).toBe(totalAntes);
});

test("o link do e-mail aponta para o frontend desta instancia, e nao para o compartilhado", async ({
  request,
  page,
  baseURL,
}) => {
  const email = emailUnico();

  await request.post(`${API}/api/v1/products/zapier/reviews`, { data: corpoDeReview(email) });
  const link = await linkDeConfirmacao(request, email);

  // A pagina de confirmacao so existe a partir da T13. O que este caso afirma e
  // que o endereco do e-mail leva a ESTA instancia: apontado para a
  // compartilhada, o fluxo de escrita mudaria o ambiente de quem so le.
  expect(new URL(link).origin).toBe(new URL(baseURL ?? "http://localhost:3002").origin);

  const home = await page.goto("/pt-BR");
  expect(home?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.homeTitulo)).toBeVisible();
});
