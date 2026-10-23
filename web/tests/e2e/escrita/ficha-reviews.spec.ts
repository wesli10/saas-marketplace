import { expect, test } from "@playwright/test";

import { TESTID } from "../../../src/lib/testid";
import { API, reviewPublicada } from "./apoio";

/**
 * Criterio de verificacao da tarefa 003-T11: AC-15 e AC-19, de ponta a ponta.
 *
 * Roda na instancia de escrita porque publica review de verdade: na
 * compartilhada, a media que este teste cria mudaria o numero que outro teste
 * afirma, com os dois em paralelo (constituicao secao 6).
 *
 * Cada caso usa o **proprio produto** pelo mesmo motivo: dois testes publicando
 * no mesmo produto disputariam a media dele.
 */
test("a nota exibida na ficha bate com a que a API devolve", async ({ page, request }) => {
  await reviewPublicada(request, { produto: "zendesk", nota: 5 });

  const daApi = await (await request.get(`${API}/api/v1/products/zendesk/reviews`)).json();

  await page.goto("/pt-BR/produtos/zendesk");

  const media = page.getByTestId(TESTID.reviewsMedia);
  await expect(media).toBeVisible();
  // Comparado com o que a API respondeu, e nao com um numero escrito aqui:
  // numero fixo viraria uma segunda fonte da verdade, e quebraria assim que
  // outro caso publicasse uma review neste produto.
  await expect(media).toHaveAttribute("data-media", String(daApi.rating.average));
  await expect(page.getByTestId(TESTID.reviewsContagem)).toHaveAttribute(
    "data-total",
    String(daApi.rating.count),
  );
});

test("AC-15: review escrita em en aparece na ficha em pt-BR, marcada como tal", async ({
  page,
  request,
}) => {
  const { titulo } = await reviewPublicada(request, { produto: "make", idioma: "en" });

  await page.goto("/pt-BR/produtos/make");

  const card = page.getByTestId(TESTID.reviewCard).filter({ hasText: titulo });
  await expect(card).toHaveAttribute("data-locale", "en");
  await expect(card.getByTestId(TESTID.reviewIdioma)).toHaveAttribute("data-idioma", "en");
  // O corpo carrega `lang`: e o que faz o leitor de tela trocar de pronuncia.
  await expect(card.getByTestId(TESTID.reviewCorpo)).toHaveAttribute("lang", "en");
});

test("a mesma review em en, lida na ficha em en, nao ganha marcacao de idioma", async ({
  page,
  request,
}) => {
  const { titulo } = await reviewPublicada(request, { produto: "n8n", idioma: "en" });

  await page.goto("/en/products/n8n");

  const card = page.getByTestId(TESTID.reviewCard).filter({ hasText: titulo });
  await expect(card).toBeVisible();
  await expect(card.getByTestId(TESTID.reviewIdioma)).toHaveCount(0);
});

test("o bloco leva para a politica de reviews, no idioma da pagina", async ({ page }) => {
  await page.goto("/en/products/zapier");

  await expect(page.getByTestId(TESTID.reviewsPolitica)).toHaveAttribute(
    "href",
    "/en/review-policy",
  );
});
