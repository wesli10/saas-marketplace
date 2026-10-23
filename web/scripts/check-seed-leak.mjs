#!/usr/bin/env node
/**
 * Cobra o AC-22: nenhum nome, slug ou preco do catalogo escrito dentro do
 * `web/`.
 *
 * O frontend renderiza o que a API devolve. Um nome de produto no codigo passa
 * despercebido - a tela fica certa - e vira mentira no dia em que a curadoria
 * mudar o dado, porque duas fontes da verdade sempre divergem. Pior: o texto
 * hardcoded nao e traduzido, entao ele aparece igual nos dois idiomas.
 *
 * O que **nao** e varrido:
 *  - `tests/`, que precisa citar dado do seed para afirmar qualquer coisa;
 *  - `src/lib/api/types.gen.ts`, gerado do OpenAPI, que traz os exemplos do
 *    proprio contrato.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const RAIZ = new URL("..", import.meta.url).pathname;
const SEED = join(RAIZ, "../src/main/resources/seed");
const ALVOS = ["src", "app"];
const IGNORADOS = [join(RAIZ, "src/lib/api/types.gen.ts")];

/** Termo curto casa com qualquer coisa ("make", "n8n"): so o que e distintivo. */
const TAMANHO_MINIMO = 5;

/**
 * Duas defesas contra falso positivo, as duas descobertas rodando a varredura:
 *
 *  1. **borda de palavra**, e nao substring: o seed tem o produto `descript`
 *     (Descript), e sem borda ele casava com a palavra `description` em quinze
 *     lugares - todos legitimos, todos campo da API;
 *  2. **comentario nao conta**: explicar um comportamento citando "Zapier" e
 *     prosa, e nao dado hardcoded. Varredura que proibe exemplo no comentario
 *     passa a ditar como se escreve, e quem le aprende a contorna-la (mesma
 *     licao do `check-testids` no T19).
 */
function semComentarios(fonte) {
  return fonte
    .replace(/\/\*[\s\S]*?\*\//g, (bloco) => bloco.replace(/[^\n]/g, " "))
    .replace(
      /(^|[^:])\/\/.*$/gm,
      (linha, antes) => antes + " ".repeat(linha.length - antes.length),
    );
}

function escapar(termo) {
  return termo.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function arquivos(dir, ext) {
  let achados = [];
  let entradas;
  try {
    entradas = readdirSync(dir);
  } catch {
    return achados;
  }
  for (const nome of entradas) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados = achados.concat(arquivos(caminho, ext));
    else if (ext.some((e) => nome.endsWith(e))) achados.push(caminho);
  }
  return achados;
}

/** Slugs e nomes do seed, direto dos arquivos versionados. */
function termosDoSeed() {
  const termos = new Set();
  for (const arquivo of arquivos(SEED, [".yml"])) {
    for (const linha of readFileSync(arquivo, "utf8").split("\n")) {
      const achado = /^\s*(slug|slug_pt|slug_en|name|name_pt|name_en)\s*:\s*(.+?)\s*$/.exec(linha);
      if (!achado) continue;
      const valor = achado[2].replace(/^["']|["']$/g, "");
      if (valor.length >= TAMANHO_MINIMO) termos.add(valor);
    }
  }
  return [...termos];
}

const termos = termosDoSeed();
if (termos.length === 0) {
  console.error("vazamento de seed: nenhum termo lido do seed - a varredura nao provaria nada");
  process.exit(1);
}

const falhas = [];
for (const alvo of ALVOS) {
  for (const arquivo of arquivos(join(RAIZ, alvo), [".ts", ".tsx"])) {
    if (IGNORADOS.includes(arquivo)) continue;
    const linhas = semComentarios(readFileSync(arquivo, "utf8")).split("\n");
    linhas.forEach((linha, indice) => {
      for (const termo of termos) {
        if (new RegExp(`\\b${escapar(termo)}\\b`).test(linha)) {
          falhas.push(`${relative(RAIZ, arquivo)}:${indice + 1}  "${termo}"\n    ${linha.trim()}`);
        }
      }
    });
  }
}

if (falhas.length > 0) {
  console.error(`vazamento de seed: ${falhas.length} ocorrencia(s)\n`);
  for (const falha of falhas) console.error(`  ${falha}`);
  console.error(
    "\nO dado vem da API. Se precisa citar o catalogo, e teste - e teste nao e varrido.",
  );
  process.exit(1);
}

console.log(`vazamento de seed: ok (${termos.length} termos conferidos)`);
