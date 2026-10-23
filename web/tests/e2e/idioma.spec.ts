import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

/**
 * Criterio de verificacao da tarefa 002-T09.
 *
 * Sem `@smoke`: o recorte de smoke tem cinco casos fixos, e o da home passou a
 * ser o de home.spec, que cobre idioma e listagem de uma vez (plano DT-10.1).
 *
 * O segmento de idioma e a unica fonte do locale, e o que nao for pt-BR nem en
 * e 404 - nao fallback silencioso. Responder 200 em /fr com conteudo em
 * portugues colocaria uma URL invalida no indice do buscador.
 */
test("a aplicacao responde em portugues no segmento pt-BR", async ({ page }) => {
  const resposta = await page.goto("/pt-BR");

  expect(resposta?.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "pt-BR");
  await expect(page.getByTestId(TESTID.homeTitulo)).toBeVisible();
});

test("o segmento en responde com lang=en", async ({ page }) => {
  const resposta = await page.goto("/en");

  expect(resposta?.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
});

test("idioma desconhecido e 404, e nao fallback", async ({ page }) => {
  const resposta = await page.goto("/fr");

  expect(resposta?.status()).toBe(404);
});
