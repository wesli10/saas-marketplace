import { defineConfig, devices } from "@playwright/test";

/**
 * Uma suite so; a smoke e um recorte por tag (plano DT-10.1). Duas suites
 * separadas divergem em uma semana - a smoke vira a que ninguem atualiza.
 *
 * Quem sobe a aplicacao e o `docker compose up -d --wait`, e nao o `webServer`
 * do Playwright: o alvo do teste e o artefato de producao, nao o `next dev`.
 */
const BASE = process.env.E2E_BASE_URL ?? "http://localhost:3000";

// Instancia com a API apontada para um endereco morto, usada so pelos testes de
// resiliencia (T14). Derrubar o `app` compartilhado mataria os outros workers.
const BASE_SEM_API = process.env.E2E_BASE_URL_SEM_API ?? "http://localhost:3001";

// Instancia com banco proprio, alvo dos testes que ESCREVEM (spec 003 secao 8).
// A constituicao secao 6 proibe a suite compartilhada de escrever: uma review
// publicada mudaria a media que outro teste afirma, com os dois em paralelo.
const BASE_ESCRITA = process.env.E2E_BASE_URL_ESCRITA ?? "http://localhost:3002";

const noCI = Boolean(process.env.CI);
const smoke = Boolean(process.env.E2E_SMOKE);

export default defineConfig({
  testDir: "tests/e2e",

  // Todo teste e independente (constituicao secao 6), entao paralelismo total.
  fullyParallel: true,
  workers: noCI ? 2 : "50%",
  forbidOnly: noCI,
  retries: noCI ? 1 : 0,

  // O orcamento da smoke e o freio que impede ela de virar a suite inteira:
  // estourar quebra o build, em vez de o CI apodrecer devagar.
  globalTimeout: smoke ? 5 * 60 * 1000 : undefined,

  reporter: noCI ? [["list"], ["html", { open: "never" }]] : [["list"]],

  use: {
    baseURL: BASE,
    trace: "on-first-retry",
  },

  projects: [
    {
      name: "chromium",
      // Os casos @no-js afirmam coisas que so valem sem JavaScript - como o
      // botao "aplicar" visivel. Sem excluir aqui, eles rodariam com o motor
      // ligado e falhariam por estarem certos.
      grepInvert: /@no-js/,
      use: { ...devices["Desktop Chrome"] },
      testIgnore: /resiliencia\/|escrita\//,
    },
    {
      name: "webkit",
      // Os casos @no-js afirmam coisas que so valem sem JavaScript - como o
      // botao "aplicar" visivel. Sem excluir aqui, eles rodariam com o motor
      // ligado e falhariam por estarem certos.
      grepInvert: /@no-js/,
      use: { ...devices["Desktop Safari"] },
      testIgnore: /resiliencia\/|escrita\//,
    },
    {
      // O AC-09 pede navegar e filtrar sem JavaScript. So os testes marcados
      // @no-js rodam aqui; o resto depende de JS por desenho.
      name: "chromium-no-js",
      use: { ...devices["Desktop Chrome"], javaScriptEnabled: false },
      grep: /@no-js/,
      testIgnore: /resiliencia\/|escrita\//,
    },
    {
      name: "mobile-chromium",
      // Os casos @no-js afirmam coisas que so valem sem JavaScript - como o
      // botao "aplicar" visivel. Sem excluir aqui, eles rodariam com o motor
      // ligado e falhariam por estarem certos.
      grepInvert: /@no-js/,
      use: { ...devices["Pixel 7"] },
      testIgnore: /resiliencia\/|escrita\//,
    },
    {
      // Alvo proprio: a instancia de escrita. Sobe com
      // `docker compose --profile e2e-escrita up`, e a suite completa a inclui.
      // Fora dela, `npm run e2e -- --project=escrita` falha na conexao - o que e
      // melhor do que escrever no ambiente compartilhado por engano.
      name: "escrita",
      testDir: "tests/e2e/escrita",
      grepInvert: /@no-js/,
      use: { ...devices["Desktop Chrome"], baseURL: BASE_ESCRITA },
    },
    {
      // O AC-17 da spec 003 pede que o formulario de avaliacao funcione sem
      // JavaScript - e ele ESCREVE, entao precisa da instancia de escrita e do
      // motor desligado ao mesmo tempo. Nenhum dos projetos existentes servia:
      // o `chromium-no-js` aponta para a instancia compartilhada, e o `escrita`
      // roda com JavaScript.
      name: "escrita-no-js",
      testDir: "tests/e2e/escrita",
      grep: /@no-js/,
      use: { ...devices["Desktop Chrome"], javaScriptEnabled: false, baseURL: BASE_ESCRITA },
    },
    {
      // Alvo proprio: a instancia sem API. Entra em uso no T14.
      name: "resiliencia",
      testDir: "tests/e2e/resiliencia",
      grepInvert: /@no-js/,
      use: { ...devices["Desktop Chrome"], baseURL: BASE_SEM_API },
    },
  ],
});
