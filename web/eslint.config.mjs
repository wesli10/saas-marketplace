import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import prettier from "eslint-config-prettier";

// `prettier` vem por ultimo de proposito: ele so desliga regras de formatacao
// que brigariam com o formatador. Quem decide formato aqui e o Prettier, e o
// ESLint cuida de correcao - duas ferramentas discutindo indentacao produz
// diff eterno.
const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  prettier,
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Gerado a partir do OpenAPI da API (T06).
    "src/lib/api/types.gen.ts",
  ]),
]);

export default eslintConfig;
