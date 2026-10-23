import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

const CATEGORIA = "/pt-BR/categorias/agentes-e-workflows";

/** Criterio de verificacao da tarefa 002-T17: AC-08. */
test("marcar um filtro muda a URL e a lista", async ({ page }) => {
  await page.goto(CATEGORIA);
  const antes = await page.getByTestId(TESTID.produtoCard).count();

  // Com JavaScript, marcar ja submete - o botao "aplicar" existe para quem nao
  // tem JS, e esta oculto aqui. A versao com botao e o teste @no-js.
  await page.getByTestId(TESTID.filtroPlanoGratuito).check();

  await expect(page).toHaveURL(/has_free_plan=true/);
  const depois = await page.getByTestId(TESTID.produtoCard).count();
  expect(depois).toBeLessThan(antes);
  expect(depois).toBeGreaterThan(0);
});

test("recarregar a URL filtrada reproduz a mesma lista", async ({ page }) => {
  await page.goto(`${CATEGORIA}?has_free_plan=true`);
  const primeira = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  await page.reload();
  const segunda = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  expect(segunda).toEqual(primeira);
  expect(primeira.length).toBeGreaterThan(0);
});

test("o filtro sobrevive ao recarregamento marcado na tela", async ({ page }) => {
  // O estado da tela sai da URL, e nao de memoria do cliente: sem isso o
  // visitante veria a lista filtrada com os controles em branco.
  await page.goto(`${CATEGORIA}?has_free_plan=true&pricing_model=FREEMIUM`);

  await expect(page.getByTestId(TESTID.filtroPlanoGratuito)).toBeChecked();
  await expect(
    page.locator(`[data-testid="${TESTID.filtroCobranca}"][data-valor="FREEMIUM"]`),
  ).toBeChecked();
  await expect(page.getByTestId(TESTID.filtroTesteGratis)).not.toBeChecked();
});

test("filtros combinam, em vez de um substituir o outro", async ({ page, request }) => {
  const url = `${CATEGORIA}?has_free_plan=true&has_free_trial=true`;
  await page.goto(url);

  const daApi = await (
    await request.get(
      "http://localhost:8080/api/v1/products?category=agentes-e-workflows&has_free_plan=true&has_free_trial=true",
    )
  ).json();

  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(daApi.items.length);
});

test("limpar volta para a categoria sem filtro nenhum", async ({ page }) => {
  await page.goto(`${CATEGORIA}?has_free_plan=true`);

  await page.getByTestId(TESTID.filtroLimpar).click();

  await expect(page).toHaveURL(CATEGORIA);
  await expect(page.getByTestId(TESTID.filtroPlanoGratuito)).not.toBeChecked();
});

test("filtro invalido vira erro da API, e nao lista errada em silencio", async ({ page }) => {
  // A validacao mora num lugar so: o frontend manda o valor como veio, e a API
  // responde 400 (plano DT-05).
  const resposta = await page.goto(`${CATEGORIA}?pricing_model=NAO_EXISTE`);

  expect(resposta?.status()).toBeGreaterThanOrEqual(400);
});

test("com JavaScript, o filtro submete sozinho e o botao some", async ({ page }) => {
  // O botao continua no HTML - some por CSS depois que o script marca o
  // formulario. Esconde-lo no servidor quebraria a versao sem JavaScript.
  await page.goto(CATEGORIA);

  await expect(page.getByTestId(TESTID.filtroAplicar)).toBeHidden();

  await page.getByTestId(TESTID.filtroPlanoGratuito).check();

  await expect(page).toHaveURL(/has_free_plan=true/);
});

test("marcar varios filtros nao enche o historico", async ({ page }) => {
  // `replace` e nao `push`: voltar dez vezes para desmarcar dez caixas nao e
  // navegacao, e castigo.
  await page.goto("/pt-BR");
  await page.getByTestId(TESTID.categoriaCard).first().click();
  await expect(page.getByTestId(TESTID.filtroPainel)).toBeVisible();

  await page.getByTestId(TESTID.filtroPlanoGratuito).check();
  await expect(page).toHaveURL(/has_free_plan=true/);
  await page.getByTestId(TESTID.filtroTesteGratis).check();
  await expect(page).toHaveURL(/has_free_trial=true/);

  await page.goBack();

  // Uma volta so devolve a home, e nao um passo intermediario de caixa marcada.
  await expect(page).toHaveURL(/\/pt-BR$/);
});
