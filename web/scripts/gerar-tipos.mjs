#!/usr/bin/env node
/**
 * Gera `src/lib/api/types.gen.ts` a partir do OpenAPI da API.
 *
 * O contrato entre os dois lados do monorepo e o documento OpenAPI
 * (constituicao secao 4): tipo escrito a mao aqui seria uma segunda fonte da
 * verdade, e a divergencia so apareceria em producao.
 *
 * O carimbo em portugues e adicionado depois da geracao, e sempre igual - se
 * variasse, a conferencia de diff no CI acusaria mudanca a cada run.
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const DESTINO = "src/lib/api/types.gen.ts";
const URL_DOCS = process.env.API_DOCS_URL ?? "http://localhost:8080/v3/api-docs";

// O healthcheck do compose diz que o Spring subiu, mas o springdoc monta o
// documento na primeira chamada, varrendo os controllers. Esperar o 200 aqui
// custa segundos e evita um passo de CI que falha de vez em quando - que e o
// pior tipo de falha, porque ninguem confia mais no gate.
async function esperarOpenApi(tentativas = 30) {
  for (let i = 1; i <= tentativas; i += 1) {
    try {
      const resposta = await fetch(URL_DOCS);
      if (resposta.ok) return;
    } catch {
      // conexao recusada enquanto sobe: e o caso normal nas primeiras voltas
    }
    if (i === tentativas) {
      throw new Error(`${URL_DOCS} nao respondeu 200 depois de ${tentativas} tentativas`);
    }
    await new Promise((ok) => setTimeout(ok, 2000));
  }
}

await esperarOpenApi();

execFileSync("npx", ["openapi-typescript", URL_DOCS, "-o", DESTINO], { stdio: "inherit" });

const CARIMBO = `/**
 * ARQUIVO GERADO - NAO EDITE A MAO.
 *
 * Fonte: o documento OpenAPI da API, em /v3/api-docs.
 * Para atualizar: suba o compose da raiz e rode \`npm run api:types\`
 * (a URL pode ser trocada pela variavel API_DOCS_URL).
 *
 * O CI regenera e compara: divergencia entre este arquivo e o contrato da API
 * quebra o build, que e o unico jeito de o contrato nao mentir.
 */
`;

const conteudo = readFileSync(DESTINO, "utf8");
writeFileSync(DESTINO, CARIMBO + conteudo);
console.log(`tipos gerados de ${URL_DOCS}`);
