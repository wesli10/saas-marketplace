#!/usr/bin/env node
/**
 * Cobra que a suite e2e rode em paralelo sem estado compartilhado, e que ela
 * nao escreva (constituicao secao 6, ACs 30, 31 e 32).
 *
 * O que esta varredura nao pega, o `--shuffle` pega: uma suite que so passa
 * numa ordem falha no primeiro push, e nao numa terca daqui a tres meses.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const RAIZ = new URL("..", import.meta.url).pathname;
const E2E = join(RAIZ, "tests/e2e");

// A suite de ESCRITA e a excecao prevista pela constituicao secao 6: ela tem
// instancia propria no compose (perfil `e2e-escrita`), e por isso pode escrever
// e falar com /admin. O que continua valendo la e o resto - paralelismo, nenhum
// estado compartilhado, e dado unico por teste.
const ESCRITA = "tests/e2e/escrita/";

const REGRAS = [
  {
    re: /describe\.serial|describe\.configure\(\s*\{[^}]*mode:\s*["']serial["']/,
    porque: "teste em serie: a suite roda em paralelo, ordem e worker sao arbitrarios",
  },
  {
    re: /^(let|var)\s+\w/,
    porque: "estado no escopo do modulo: dois testes no mesmo worker passariam a se enxergar",
  },
  {
    re: /beforeAll\s*\(/,
    porque: "beforeAll prepara estado compartilhado; cada teste monta o proprio ponto de partida",
  },
  {
    re: /request\.(post|put|patch|delete)\s*\(|method:\s*["'](POST|PUT|PATCH|DELETE)["']/,
    porque: "a suite de leitura nao escreve: cenario com outro estado ganha outra instancia",
    soForaDaEscrita: true,
  },
  {
    re: /\/api\/v1\/admin\//,
    porque: "rota de curadoria num teste de leitura",
    soForaDaEscrita: true,
  },
  {
    // Dentro da escrita, o perigo se inverte: nao e escrever, e reaproveitar
    // dado. O indice unico da V7 e por (produto, e-mail), entao um endereco
    // fixo faz o segundo teste que o usar nao criar nada - e falhar por um
    // motivo que nao e o dele.
    // O `$` fora da classe deixa passar o interpolado (`${uuid}@teste.local`),
    // que e justamente a forma certa - e a que o emailUnico() usa.
    re: /["'`][^"'`\s$]+@[a-z0-9.-]+\.[a-z]{2,}["'`]/i,
    porque: "e-mail literal na suite de escrita: use emailUnico() (constituicao secao 6)",
    soNaEscrita: true,
  },
];

const falhas = [];

function arquivos(dir) {
  let achados = [];
  let entradas;
  try {
    entradas = readdirSync(dir);
  } catch {
    return achados;
  }
  for (const nome of entradas) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados = achados.concat(arquivos(caminho));
    else if (nome.endsWith(".ts")) achados.push(caminho);
  }
  return achados;
}

for (const arquivo of arquivos(E2E)) {
  const linhas = readFileSync(arquivo, "utf8").split("\n");
  const naEscrita = relative(RAIZ, arquivo).includes(ESCRITA);

  linhas.forEach((linha, i) => {
    const limpa = linha.replace(/\/\/.*$/, "").replace(/^\s*\*.*$/, "");

    for (const regra of REGRAS) {
      if (regra.soForaDaEscrita && naEscrita) continue;
      if (regra.soNaEscrita && !naEscrita) continue;
      if (regra.re.test(limpa)) {
        falhas.push(`${relative(RAIZ, arquivo)}:${i + 1}  ${regra.porque}\n    ${linha.trim()}`);
      }
    }

    // getByText serve para afirmar sobre o texto, nao para alcancar elemento:
    // a interface e bilingue, e seletor por texto morre no primeiro ?lang=en.
    if (limpa.includes("getByText(") && !limpa.includes("expect(")) {
      falhas.push(
        `${relative(RAIZ, arquivo)}:${i + 1}  getByText fora de assercao - use getByTestId` +
          `\n    ${linha.trim()}`,
      );
    }
  });
}

if (falhas.length > 0) {
  console.error("isolamento do e2e: " + falhas.length + " problema(s)\n");
  for (const f of falhas) console.error("  " + f);
  process.exit(1);
}
console.log("isolamento do e2e: ok");
