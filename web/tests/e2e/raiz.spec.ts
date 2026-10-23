import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T10: AC-03.
 *
 * Confere o **307**, e nao so o destino: um 308 na raiz ficaria memorizado pelo
 * navegador, e o idioma preferido do visitante pode mudar.
 */
test("a raiz manda para o ingles quando o navegador pede ingles", async ({ request }) => {
  const resposta = await request.get("/", {
    headers: { "Accept-Language": "en-US,en;q=0.9" },
    maxRedirects: 0,
  });

  expect(resposta.status()).toBe(307);
  expect(resposta.headers()["location"]).toContain("/en");
});

test("sem cabecalho, a raiz vai para o portugues", async ({ request }) => {
  const resposta = await request.get("/", { maxRedirects: 0 });

  expect(resposta.status()).toBe(307);
  expect(resposta.headers()["location"]).toContain("/pt-BR");
});

test("idioma que nao servimos cai no padrao, sem erro", async ({ request }) => {
  const resposta = await request.get("/", {
    headers: { "Accept-Language": "fr-FR,fr;q=0.9" },
    maxRedirects: 0,
  });

  expect(resposta.status()).toBe(307);
  expect(resposta.headers()["location"]).toContain("/pt-BR");
});
