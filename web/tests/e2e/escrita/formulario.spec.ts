import { expect, test } from "@playwright/test";

import { TESTID } from "../../../src/lib/testid";
import { API, emailUnico, linkDeConfirmacao, tituloDe } from "./apoio";

/**
 * Criterio de verificacao da tarefa 003-T12: AC-17 e AC-18.
 *
 * O caso `@no-js` e o que justifica a tarefa existir: o plano registrou como
 * risco R-02 que a Server Action pode nao degradar como o Next documenta, e a
 * unica forma de saber e desligar o motor e enviar de verdade.
 */
async function preencher(page: import("@playwright/test").Page, email: string) {
  // Por testid e atributo, e nao por posicao: `nth(3)` quebraria em silencio se
  // a ordem das notas mudasse, e passaria a testar outra coisa.
  await page.getByTestId(TESTID.avaliarNotaOpcao).and(page.locator('[data-nota="4"]')).check();
  await page
    .getByTestId(TESTID.avaliarRecomendariaOpcao)
    .and(page.locator('[data-valor="true"]'))
    .check();
  await page.getByTestId(TESTID.avaliarTitulo).fill(tituloDe(email));
  await page
    .getByTestId(TESTID.avaliarCorpo)
    .fill(
      "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade. A configuracao inicial levou uma tarde.",
    );
  await page.getByTestId(TESTID.avaliarNome).fill("Ana Ribeiro");
  await page.getByTestId(TESTID.avaliarEmail).fill(email);
}

test("AC-18: enviar cria a review, a tela diz que esta em analise, e ela nao aparece na ficha", async ({
  page,
  request,
}) => {
  const email = emailUnico();

  await page.goto("/pt-BR/produtos/intercom/avaliar");
  await preencher(page, email);
  await page.getByTestId(TESTID.avaliarEnviar).click();

  // A tela diz com todas as letras que esta em analise - e nao que sumiu.
  await expect(page.getByTestId(TESTID.avaliarEmAnalise)).toBeVisible();

  // A review existe: o e-mail de confirmacao chegou ao Mailpit.
  const link = await linkDeConfirmacao(request, email);
  expect(link).toContain("token=");

  // E nao aparece na ficha: falta confirmar o e-mail e passar pela curadoria.
  await page.goto("/pt-BR/produtos/intercom");
  await expect(
    page.getByTestId(TESTID.reviewCard).filter({ hasText: tituloDe(email) }),
  ).toHaveCount(0);
});

test("erro de validacao volta com o texto que a pessoa escreveu", async ({ page }) => {
  const email = emailUnico();

  await page.goto("/pt-BR/produtos/freshdesk/avaliar");
  await preencher(page, email);
  // E-mail invalido: tudo o resto esta certo, e nada disso pode se perder.
  await page.getByTestId(TESTID.avaliarEmail).fill("ana-arroba-acme");
  await page.getByTestId(TESTID.avaliarEnviar).click();

  await expect(
    page.getByTestId(TESTID.avaliarErro).filter({ hasText: /e-mail|email/i }),
  ).toBeVisible();
  await expect(page.getByTestId(TESTID.avaliarCorpo)).toHaveValue(/oito meses/);
  await expect(page.getByTestId(TESTID.avaliarTitulo)).toHaveValue(tituloDe(email));
});

test("AC-17: o envio funciona com JavaScript desligado @no-js", async ({ page, request }) => {
  const email = emailUnico();

  await page.goto("/pt-BR/produtos/octadesk/avaliar");
  await preencher(page, email);
  await page.getByTestId(TESTID.avaliarEnviar).click();

  // Sem JavaScript o navegador faz o POST e segue o redirect do servidor: a
  // URL muda, e a tela de "em analise" e HTML servido, nao estado de cliente.
  await expect(page).toHaveURL(/enviado=1/);
  await expect(page.getByTestId(TESTID.avaliarEmAnalise)).toBeVisible();

  const link = await linkDeConfirmacao(request, email);
  expect(link).toContain("token=");
});

test("AC-17: sem JavaScript, o erro tambem volta com os campos preenchidos @no-js", async ({
  page,
}) => {
  const email = emailUnico();

  await page.goto("/pt-BR/produtos/movidesk/avaliar");
  await preencher(page, email);
  await page.getByTestId(TESTID.avaliarCorpo).fill("bom");
  await page.getByTestId(TESTID.avaliarEnviar).click();

  await expect(page.getByTestId(TESTID.avaliarResumoDeErros)).toBeVisible();
  await expect(page.getByTestId(TESTID.avaliarErro).filter({ hasText: /80/ })).toBeVisible();
  // O que a pessoa escreveu continua ali, depois de uma volta inteira ao
  // servidor sem JavaScript nenhum.
  await expect(page.getByTestId(TESTID.avaliarTitulo)).toHaveValue(tituloDe(email));
  await expect(page.getByTestId(TESTID.avaliarEmail)).toHaveValue(email);
  await expect(page.getByTestId(TESTID.avaliarNome)).toHaveValue("Ana Ribeiro");
});

test("a ficha leva ao formulario, no idioma da pagina", async ({ page }) => {
  await page.goto("/en/products/jasper");

  await expect(page.getByTestId(TESTID.fichaAvaliar)).toHaveAttribute(
    "href",
    "/en/products/jasper/review",
  );
});

test("o formulario recusa produto que nao existe", async ({ page }) => {
  const resposta = await page.goto("/pt-BR/produtos/nao-existe/avaliar");

  expect(resposta?.status()).toBe(404);
});
