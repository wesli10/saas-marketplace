import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

/**
 * Vitest cobre unidade e componente. Fluxo e Playwright, e vive em tests/e2e -
 * por isso a exclusao explicita aqui: sem ela o Vitest tenta coletar os
 * arquivos .spec.ts do Playwright e quebra com um erro que nao explica nada.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/unit/**/*.test.{ts,tsx}"],
    exclude: ["node_modules", ".next", "tests/e2e/**"],
  },
  resolve: {
    alias: {
      "@": new URL("./", import.meta.url).pathname,
      // `server-only` lanca quando resolvido fora da condicao react-server, que
      // e exatamente o trabalho dele - e o que impediria o Vitest de importar o
      // cliente da API. Aqui ele vira modulo vazio; a guarda que importa e a do
      // build, e essa continua valendo.
      "server-only": new URL("./tests/stubs/server-only.ts", import.meta.url).pathname,
    },
  },
});
