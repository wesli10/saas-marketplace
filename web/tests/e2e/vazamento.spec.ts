import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T27: AC-24.
 *
 * A guarda de verdade e o pacote `server-only` (T12), que quebra o BUILD se o
 * cliente da API for importado do navegador. Este teste cobre o que ela nao
 * pega: o valor vazando pelo payload de hidratacao, por passar `API_BASE_URL`
 * como prop de um componente de cliente. Por isso a varredura e no HTML
 * SERVIDO e nos scripts que a pagina carrega - e nao no codigo-fonte.
 */
const SEGREDOS = [
  "http://app:8080", // o endereco interno do compose
  "API_BASE_URL",
  "ADMIN_PASSWORD",
];

const PAGINAS = ["/pt-BR", "/pt-BR/categorias/agentes-e-workflows", "/pt-BR/produtos/zapier"];

for (const rota of PAGINAS) {
  test(`AC-24: nada de endereco interno no HTML servido: ${rota}`, async ({ request }) => {
    // `request`, e nao `page`: interessa o que o servidor mandou, antes de
    // qualquer script rodar.
    const html = await (await request.get(rota)).text();

    for (const segredo of SEGREDOS) expect(html).not.toContain(segredo);
  });
}

test("AC-24: nada de endereco interno nos scripts que a pagina carrega", async ({
  page,
  request,
}) => {
  await page.goto("/pt-BR/categorias/agentes-e-workflows");

  const scripts = await page
    .locator("script[src]")
    .evaluateAll((nos) => nos.map((no) => no.getAttribute("src") ?? ""));
  expect(scripts.length).toBeGreaterThan(0);

  for (const src of scripts) {
    const corpo = await (await request.get(src)).text();
    for (const segredo of SEGREDOS) {
      expect(corpo, `${src} carrega "${segredo}"`).not.toContain(segredo);
    }
  }
});

test("a URL publica do site aparece, e isso e o esperado", async ({ request }) => {
  // Contraprova: se nenhuma URL aparecesse, o teste acima passaria por engano -
  // num HTML sem URL nenhuma ele nao provaria nada.
  const html = await (await request.get("/pt-BR/produtos/zapier")).text();

  expect(html).toContain("http://localhost:3000/pt-BR/produtos/zapier");
});
