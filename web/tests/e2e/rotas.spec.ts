import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T11.
 *
 * O que importa aqui nao e a pagina responder - e a combinacao errada NAO
 * responder. Os dois segmentos do par vivem sob [locale], entao sem a guarda
 * `/pt-BR/categories/x` renderizaria igualzinho, e existiriam duas URLs para a
 * mesma coisa - o problema que o prefixo obrigatorio existe para evitar.
 */
const pares = [
  {
    pt: "/pt-BR/categorias/atendimento-e-chatbots",
    en: "/en/categories/customer-support-and-chatbots",
  },
  { pt: "/pt-BR/produtos/zapier", en: "/en/products/zapier" },
  { pt: "/pt-BR/buscar", en: "/en/search" },
  { pt: "/pt-BR/comparar", en: "/en/compare" },
];

for (const par of pares) {
  test(`${par.pt} e ${par.en} respondem`, async ({ page }) => {
    expect((await page.goto(par.pt))?.status()).toBe(200);
    expect((await page.goto(par.en))?.status()).toBe(200);
  });

  test(`segmento trocado com o idioma e 404: ${par.en.replace("/en", "/pt-BR")}`, async ({
    page,
  }) => {
    const cruzado = par.en.replace("/en", "/pt-BR");
    expect((await page.goto(cruzado))?.status()).toBe(404);
  });
}
