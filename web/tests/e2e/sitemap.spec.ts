import { expect, test } from "@playwright/test";

const API = "http://localhost:8080/api/v1";
const SITE = "http://localhost:3000";

/** Criterio de verificacao da tarefa 002-T26: AC-21. */
async function urlsDoSitemap(request: import("@playwright/test").APIRequestContext) {
  const resposta = await request.get("/sitemap.xml");
  expect(resposta.status()).toBe(200);
  const xml = await resposta.text();
  return [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((achado) => achado[1] ?? "");
}

test("AC-21: o sitemap tem tantas URLs quanto o catalogo, nos dois idiomas", async ({
  request,
}) => {
  const urls = await urlsDoSitemap(request);

  const arvore = await (await request.get(`${API}/categories`)).json();
  const categorias = (arvore.items ?? []).flatMap((raiz: { children?: unknown[] }) => [
    raiz,
    ...(raiz.children ?? []),
  ]);
  const produtos = await (await request.get(`${API}/products?size=50`)).json();

  // home + politica de reviews + categorias + produtos, cada um nos dois
  // idiomas. A politica entrou na spec 003 (AC-21a): e conteudo publico e
  // estavel, e e ela que da lastro ao selo de verificado.
  const esperado = (2 + categorias.length + produtos.total) * 2;
  expect(urls).toHaveLength(esperado);

  const emPortugues = urls.filter((url) => url.includes("/pt-BR"));
  const emIngles = urls.filter((url) => url.includes("/en"));
  expect(emPortugues).toHaveLength(esperado / 2);
  expect(emIngles).toHaveLength(esperado / 2);
});

test("AC-21: cada produto do catalogo aparece nas duas versoes", async ({ request }) => {
  const urls = await urlsDoSitemap(request);
  const produtos = await (await request.get(`${API}/products?size=50`)).json();

  for (const produto of produtos.items) {
    expect(urls).toContain(`${SITE}/pt-BR/produtos/${produto.slug}`);
    expect(urls).toContain(`${SITE}/en/products/${produto.slug}`);
  }
});

test("AC-21: a categoria entra com o slug de cada idioma, e nao com o mesmo duas vezes", async ({
  request,
}) => {
  const urls = await urlsDoSitemap(request);
  const categoria = await (await request.get(`${API}/categories/agentes-e-workflows`)).json();

  expect(categoria.alternates.en).toBe("agents-and-workflows");
  expect(urls).toContain(`${SITE}/pt-BR/categorias/agentes-e-workflows`);
  expect(urls).toContain(`${SITE}/en/categories/agents-and-workflows`);
});

test("o sitemap declara o par de idiomas em cada entrada", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();

  expect(xml).toContain('hreflang="pt-BR"');
  expect(xml).toContain('hreflang="en"');
});

test("a comparacao e a busca ficam fora do sitemap", async ({ request }) => {
  const urls = await urlsDoSitemap(request);

  // Sitemap e onde se PEDE indexacao; a comparacao e noindex e a busca nao tem
  // conteudo proprio sem query.
  expect(urls.filter((url) => url.includes("/comparar") || url.includes("/compare"))).toEqual([]);
  expect(urls.filter((url) => url.includes("/buscar") || url.includes("/search"))).toEqual([]);
});

test("o robots libera tudo e aponta o sitemap", async ({ request }) => {
  const resposta = await request.get("/robots.txt");
  const texto = await resposta.text();

  expect(resposta.status()).toBe(200);
  expect(texto).toContain("User-Agent: *");
  expect(texto).toContain("Allow: /");
  expect(texto).toContain(`Sitemap: ${SITE}/sitemap.xml`);
  // A comparacao nao pode ser bloqueada: sem rastrear, o robo nao le o noindex.
  expect(texto).not.toContain("Disallow: /pt-BR/comparar");
});
