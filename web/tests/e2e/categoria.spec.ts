import { expect, test } from "@playwright/test";

import { TESTID } from "../../src/lib/testid";

const CATEGORIA_PT = "atendimento-e-chatbots";
const CATEGORIA_EN = "customer-support-and-chatbots";

/** Criterio de verificacao da tarefa 002-T16. */
test("a categoria lista os produtos do seed @smoke", async ({ page, request }) => {
  const resposta = await page.goto(`/pt-BR/categorias/${CATEGORIA_PT}`);
  expect(resposta?.status()).toBe(200);

  const daApi = await (
    await request.get(`http://localhost:8080/api/v1/products?category=${CATEGORIA_PT}`)
  ).json();

  await expect(page.getByTestId(TESTID.produtoCard)).toHaveCount(daApi.items.length);
  await expect(page.getByTestId(TESTID.listaContagem)).toContainText(String(daApi.total));
});

test("os slugs na pagina sao os mesmos que a API devolve, na mesma ordem", async ({
  page,
  request,
}) => {
  await page.goto(`/pt-BR/categorias/${CATEGORIA_PT}`);

  const daApi = await (
    await request.get(`http://localhost:8080/api/v1/products?category=${CATEGORIA_PT}`)
  ).json();

  const naPagina = await page
    .getByTestId(TESTID.produtoCard)
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("data-slug")));

  expect(naPagina).toEqual(daApi.items.map((p: { slug: string }) => p.slug));
});

test("produto sem preco diz 'sob consulta' em vez de zero", async ({ page, request }) => {
  // Tres produtos do seed nao tem preco. Mostrar R$ 0,00 seria informacao errada.
  //
  // O campo vem como `null`, e nao ausente: a API usa
  // `default-property-inclusion: always`. O OpenAPI nao marca anulaveis, entao
  // o tipo gerado diz `number | undefined` - por isso a comparacao e `== null`,
  // que cobre os dois casos (plano DT-03).
  const daApi = await (await request.get("http://localhost:8080/api/v1/products?size=50")).json();
  const semPreco = daApi.items.find((p: { price_min?: number | null }) => p.price_min == null);
  expect(semPreco, "o seed precisa ter ao menos um produto sem preco").toBeTruthy();

  await page.goto(`/pt-BR/categorias/${semPreco.category.slug}`);
  const preco = page.locator(
    `[data-slug="${semPreco.slug}"] [data-testid="${TESTID.produtoPreco}"]`,
  );

  await expect(preco).toHaveText("Preço sob consulta");
});

test("card sem logo mantem a altura da linha", async ({ page }) => {
  // Nenhum produto do seed tem logo hoje, entao o fallback e o caminho normal:
  // se ele encolhesse, a lista mudaria de altura no dia em que houver o primeiro.
  await page.goto(`/pt-BR/categorias/${CATEGORIA_PT}`);

  const logos = page.getByTestId(TESTID.produtoLogo);
  const alturas = await logos.evaluateAll((nos) =>
    nos.map((no) => no.getBoundingClientRect().height),
  );

  expect(alturas.length).toBeGreaterThan(0);
  expect(new Set(alturas).size, "todos os blocos de logo tem a mesma altura").toBe(1);
  expect(alturas[0]).toBeGreaterThan(0);
});

test("em ingles, a lista sai com o slug e o conteudo ingleses", async ({ page }) => {
  await page.goto(`/en/categories/${CATEGORIA_EN}`);

  await expect(page.getByTestId(TESTID.listaTitulo)).toBeVisible();
  const primeiro = page.getByTestId(TESTID.produtoCard).first();
  const slug = await primeiro.getAttribute("data-slug");
  await expect(primeiro).toHaveAttribute("href", `/en/products/${slug}`);
});
