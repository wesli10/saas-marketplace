#!/usr/bin/env node
/**
 * Cobra a convencao de `data-testid` (constituicao secao 6, ACs 26 e 27).
 *
 * Duas regras:
 *  1. todo elemento interativo em src/components/ tem data-testid;
 *  2. nenhum id sai de dado do seed - id derivado de conteudo amarra o teste
 *     aos 20 produtos do 001, e trocar o seed quebraria a suite inteira.
 *
 * Analise textual, de proposito: uma regra de ESLint dedicada custa mais do que
 * resolve nesta fatia. O que ela nao pega - se o id e *bom* - e revisao humana,
 * e por isso a convencao esta na constituicao, e nao so aqui.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const RAIZ = new URL("..", import.meta.url).pathname;
const COMPONENTES = join(RAIZ, "src/components");
const SEED = join(RAIZ, "../src/main/resources/seed");
const INTERATIVOS = ["button", "a", "input", "select", "textarea", "form"];

const falhas = [];

function arquivos(dir, ext) {
  let achados = [];
  let entradas;
  try {
    entradas = readdirSync(dir);
  } catch {
    return achados; // diretorio ainda nao existe: nada a verificar
  }
  for (const nome of entradas) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados = achados.concat(arquivos(caminho, ext));
    else if (ext.some((e) => nome.endsWith(e))) achados.push(caminho);
  }
  return achados;
}

/**
 * Comentario de bloco vira espaco antes da varredura, preservando o tamanho
 * para as linhas continuarem batendo.
 *
 * Sem isto, explicar `<a href>` no comentario de um componente e acusado como
 * link sem id: a varredura passa a ditar o que se pode escrever em prosa, e
 * quem le aprende a contornar a regra em vez de segui-la (T19).
 */
function semComentarios(fonte) {
  return fonte.replace(/\/\*[\s\S]*?\*\//g, (bloco) => bloco.replace(/[^\n]/g, " "));
}

// --- regra 1: interativo sem testid ---
for (const arquivo of arquivos(COMPONENTES, [".tsx"])) {
  const fonte = semComentarios(readFileSync(arquivo, "utf8"));
  const linhas = readFileSync(arquivo, "utf8").split("\n");

  for (const tag of INTERATIVOS) {
    // Captura a tag de abertura inteira, inclusive quebrada em varias linhas.
    const re = new RegExp(`<${tag}(\\s[^>]*?)?/?>`, "gs");
    for (const achado of fonte.matchAll(re)) {
      if (achado[0].includes("data-testid")) continue;
      // Campo oculto nao e alcancavel por teste nem clicavel: exigir id dele
      // seria ruido, e ruido treina quem le a ignorar a varredura inteira.
      if (/type=\{?["']hidden["']\}?/.test(achado[0])) continue;
      const linha = fonte.slice(0, achado.index).split("\n").length;
      falhas.push(
        `${relative(RAIZ, arquivo)}:${linha}  <${tag}> sem data-testid` +
          `\n    ${linhas[linha - 1]?.trim() ?? ""}`,
      );
    }
  }
}

// --- regra 2: id que veio do seed ---
const termosDoSeed = new Set();
for (const arquivo of arquivos(SEED, [".yml"])) {
  const fonte = readFileSync(arquivo, "utf8");
  for (const achado of fonte.matchAll(/^\s*(?:slug|slug_pt|slug_en):\s*"?([a-z0-9-]+)"?/gm)) {
    if (achado[1] && achado[1].length > 3) termosDoSeed.add(achado[1]);
  }
}

const testids = join(RAIZ, "src/lib/testid.ts");
try {
  const fonte = readFileSync(testids, "utf8");
  for (const achado of fonte.matchAll(/"([a-z0-9-]+)"/g)) {
    const id = achado[1];
    if (id && termosDoSeed.has(id)) {
      falhas.push(`src/lib/testid.ts  id "${id}" e um slug do seed - ids nao saem de dado`);
    }
  }
} catch {
  falhas.push("src/lib/testid.ts nao existe: os ids precisam viver num lugar so");
}

if (falhas.length > 0) {
  console.error("data-testid: " + falhas.length + " problema(s)\n");
  for (const f of falhas) console.error("  " + f);
  process.exit(1);
}
console.log("data-testid: ok");
