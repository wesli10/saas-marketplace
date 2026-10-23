import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

// A raiz agrega os 20 produtos do seed (as quatro subcategorias tem 5 cada),
// entao e a unica categoria que passa do tamanho de pagina padrao.
const RAIZ = "/pt-BR/categorias/ia-e-automacao-para-pmes";
const SUBCATEGORIA = "/pt-BR/categorias/agentes-e-workflows";

/** Criterio de verificacao da tarefa 002-T19: AC-10. */
test("a proxima pagina traz outros produtos, e o link volta para os primeiros", async ({
  page,
}) => {
  await page.goto(RAIZ);
  const primeira = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  await page.getByTestId(TESTID.paginacaoProxima).click();

  await expect(page).toHaveURL(/page=1/);
  const segunda = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  expect(segunda.length).toBeGreaterThan(0);
  expect(segunda.filter((slug) => primeira.includes(slug))).toEqual([]);

  await page.getByTestId(TESTID.paginacaoAnterior).click();

  // A primeira pagina nao carrega `page=`: uma URL so para ela.
  await expect(page).toHaveURL(RAIZ);
  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(primeira.length);
});

test("voltar no navegador depois de paginar devolve a lista filtrada", async ({ page }) => {
  // `size=3` porque o filtro deixa 7 produtos: o AC pede paginar *dentro* de uma
  // lista filtrada, e nao paginar e filtrar em telas diferentes.
  const filtrada = `${RAIZ}?has_free_plan=true&size=3`;
  await page.goto(filtrada);
  const antes = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  await page.getByTestId(TESTID.paginacaoProxima).click();
  await expect(page).toHaveURL(/page=1/);

  await page.goBack();

  await expect(page).toHaveURL(filtrada);
  await expect(page.getByTestId(TESTID.filtroPlanoGratuito)).toBeChecked();
  const depois = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));
  expect(depois).toEqual(antes);
});

test("paginar preserva o filtro na propria URL", async ({ page }) => {
  await page.goto(`${RAIZ}?has_free_plan=true&size=3`);

  await page.getByTestId(TESTID.paginacaoProxima).click();

  await expect(page).toHaveURL(/has_free_plan=true/);
  await expect(page).toHaveURL(/size=3/);
});

test("os links de pagina sao rastreaveis, com rel prev e next", async ({ page }) => {
  // Sao `<a href>` de verdade (spec secao 5.2): o robo de busca segue, e o
  // `rel` diz a ele qual e a ordem.
  await page.goto(`${RAIZ}?size=3&page=1`);

  await expect(page.getByTestId(TESTID.paginacaoProxima)).toHaveAttribute("rel", "next");
  await expect(page.getByTestId(TESTID.paginacaoAnterior)).toHaveAttribute("rel", "prev");
  await expect(page.getByTestId(TESTID.paginacaoProxima)).toHaveAttribute("href", /page=2/);
});

test("na primeira pagina nao ha link de anterior", async ({ page }) => {
  await page.goto(RAIZ);

  await expect(page.getByTestId(TESTID.paginacaoStatus)).toHaveText("Página 1 de 2");
  await expect(page.getByTestId(TESTID.paginacaoAnterior)).toHaveCount(0);
});

test("lista que cabe numa pagina nao mostra paginacao", async ({ page }) => {
  await page.goto(SUBCATEGORIA);

  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
  await expect(page.getByTestId(TESTID.paginacao)).toHaveCount(0);
});

test("pagina fora da faixa oferece a volta para a ultima que existe", async ({ page }) => {
  // Nenhum link leva aqui - so URL digitada a mao. Este teste so pode afirmar o
  // "anterior" porque o T28 corrigiu o `total`: enquanto a API devolvia
  // `total: 0` alem do fim, o frontend nao tinha como saber que existem 2
  // paginas. O destino e a ultima pagina que existe, e nao a vizinha vazia.
  const resposta = await page.goto(`${RAIZ}?page=90`);

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.listaVazia)).toBeVisible();
  await expect(page.getByTestId(TESTID.paginacaoAnterior)).toHaveAttribute("href", /page=1/);
  await expect(page.getByTestId(TESTID.paginacaoProxima)).toHaveCount(0);
});
