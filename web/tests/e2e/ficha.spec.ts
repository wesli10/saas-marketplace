import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

// Slug de produto nao muda com o idioma (spec 001 secao 4.2): so o segmento.
const PRODUTO = "zapier";

/** Criterio de verificacao da tarefa 002-T22: AC-14. Quarto caso da smoke. */
test("a ficha abre pelo slug @smoke", async ({ page }) => {
  const resposta = await page.goto(`/pt-BR/produtos/${PRODUTO}`);

  expect(resposta?.status()).toBe(200);
  await expect(page.getByTestId(TESTID.fichaTitulo)).toHaveText("Zapier");
});

test("a ficha traz o conteudo curado dos dois idiomas", async ({ page, request }) => {
  const versoes: [string, string][] = [
    [`/pt-BR/produtos/${PRODUTO}`, "pt-BR"],
    [`/en/products/${PRODUTO}`, "en"],
  ];

  for (const [rota, idioma] of versoes) {
    await page.goto(rota);

    const daApi = await (
      await request.get(`http://localhost:8080/api/v1/products/${PRODUTO}`, {
        headers: { "Accept-Language": idioma },
      })
    ).json();

    await expect(page.getByTestId(TESTID.fichaTagline)).toHaveText(daApi.tagline);
    await expect(page.getByTestId(TESTID.fichaPro)).toHaveCount(daApi.pros.length);
    await expect(page.getByTestId(TESTID.fichaCon)).toHaveCount(daApi.cons.length);
    await expect(page.getByTestId(TESTID.fichaFaqItem)).toHaveCount(daApi.faq.length);
    await expect(page.getByTestId(TESTID.fichaMelhorPara)).toHaveText(daApi.best_for);
    // O seed nao tem produto sem curadoria - e nao pode ter (AC-14 vive no
    // teste de componente). Aqui o aviso tem que estar ausente.
    await expect(page.getByTestId(TESTID.fichaTraducaoAusente)).toHaveCount(0);
  }
});

test("o link para o site do produto nao passa autoridade", async ({ page }) => {
  await page.goto(`/pt-BR/produtos/${PRODUTO}`);

  const site = page.getByTestId(TESTID.fichaSite);
  await expect(site).toHaveAttribute("rel", "nofollow sponsored noopener");
  await expect(site).toHaveAttribute("target", "_blank");
  await expect(site).toHaveAttribute("href", /^https?:\/\//);
});

test("a ficha diz de quando e o que esta mostrando", async ({ page }) => {
  await page.goto(`/pt-BR/produtos/${PRODUTO}`);

  // Transparencia e posicionamento do produto: preco sem data e preco sem valor.
  await expect(page.getByTestId(TESTID.fichaAtualizado)).toContainText("Atualizado em");
});

test("a categoria da ficha leva para a lista daquela categoria", async ({ page }) => {
  await page.goto(`/pt-BR/produtos/${PRODUTO}`);

  await page.getByTestId(TESTID.fichaCategoria).click();

  await expect(page).toHaveURL(/\/pt-BR\/categorias\//);
  await expect(page.getByTestId(TESTID.produtoCard).first()).toBeVisible();
});

test("da lista para a ficha, pelo card @no-js", async ({ page }) => {
  // O caminho normal do visitante, e sem JavaScript nenhum.
  await page.goto("/pt-BR/categorias/agentes-e-workflows");

  const primeiro = page.getByTestId(TESTID.produtoCard).first();
  const slug = await primeiro.getAttribute("data-slug");
  await primeiro.click();

  await expect(page).toHaveURL(new RegExp(`/pt-BR/produtos/${slug}$`));
  await expect(page.getByTestId(TESTID.fichaTitulo)).toBeVisible();
});
