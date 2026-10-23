import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

import { TESTID } from "../../../src/lib/testid";
import { API, corpoDeReview, emailUnico, linkDeConfirmacao, reviewPublicada } from "./apoio";

/**
 * Criterio de verificacao da tarefa 003-T15, parte do AC-21.
 *
 * As telas que so existem depois de alguem escrever ficam aqui, e nao no axe da
 * suite compartilhada: a ficha COM avaliacao publicada, a tela de "em analise" e
 * a de confirmacao. Rodar isto na instancia compartilhada exigiria publicar uma
 * avaliacao la - que e exatamente o que a constituicao secao 6 proibe.
 *
 * O corte e o mesmo do 002: critical e serious. Violacao moderate/minor do axe
 * traz muita heuristica que gera ruido, e travar o build nelas treina quem le a
 * ignorar o relatorio inteiro.
 */
async function semViolacaoGrave(page: import("@playwright/test").Page) {
  const resultado = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();

  const graves = resultado.violations.filter(
    (violacao) => violacao.impact === "critical" || violacao.impact === "serious",
  );

  const descricao = graves
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)
    .join("\n");

  expect(graves, descricao).toHaveLength(0);
}

test("axe na ficha com avaliacao publicada", async ({ page, request }) => {
  await reviewPublicada(request, { produto: "wati" });

  await page.goto("/pt-BR/produtos/wati");
  await expect(page.getByTestId(TESTID.reviewCard).first()).toBeVisible();

  await semViolacaoGrave(page);
});

test("axe na tela de avaliacao enviada", async ({ page }) => {
  await page.goto("/pt-BR/produtos/twilio-whatsapp/avaliar?enviado=1");
  await expect(page.getByTestId(TESTID.avaliarEmAnalise)).toBeVisible();

  await semViolacaoGrave(page);
});

test("axe na tela de confirmacao, no sucesso e no link invalido", async ({ page, request }) => {
  const email = emailUnico();
  await request.post(`${API}/api/v1/products/take-blip/reviews`, { data: corpoDeReview(email) });
  const link = await linkDeConfirmacao(request, email);

  await page.goto(link);
  await expect(page.getByTestId(TESTID.confirmacaoSucesso)).toBeVisible();
  await semViolacaoGrave(page);

  // O mesmo link de novo: a tela de link invalido, com o formulario de reenvio.
  await page.goto(link);
  await expect(page.getByTestId(TESTID.confirmacaoReenvioFormulario)).toBeVisible();
  await semViolacaoGrave(page);
});
