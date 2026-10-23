import { describe, expect, it } from "vitest";

import { textos } from "@/src/lib/i18n";
import { LOCALES } from "@/src/lib/i18n/locales";

/**
 * Criterio de verificacao da tarefa 002-T13.
 *
 * A paridade de chaves e garantida pelo tipo, e nao por teste - remover uma
 * chave do ingles quebra o typecheck, o que acontece antes de qualquer teste
 * rodar. O que sobra para o teste e o que o tipo nao pega: valor vazio, e texto
 * que ficou igual nos dois idiomas por esquecimento de traduzir.
 */
function achatar(objeto: object, prefixo = ""): [string, string][] {
  return Object.entries(objeto).flatMap(([chave, valor]) =>
    typeof valor === "string"
      ? [[`${prefixo}${chave}`, valor] as [string, string]]
      : achatar(valor as object, `${prefixo}${chave}.`),
  );
}

describe("catalogo de textos", () => {
  it("nenhum texto vazio, em nenhum idioma", () => {
    for (const locale of LOCALES) {
      for (const [chave, valor] of achatar(textos(locale))) {
        expect(valor.trim(), `${locale} → ${chave}`).not.toBe("");
      }
    }
  });

  it("nenhum texto identico nos dois idiomas", () => {
    // Texto igual nos dois catalogos quase sempre e traducao esquecida. As
    // excecoes sao explicitas, uma a uma, com o motivo - lista vazia era o
    // estado ate o T17 trazer o primeiro caso legitimo.
    const excecoes = new Set<string>([
      // "Freemium" e a mesma palavra em portugues e ingles. Traduzir para
      // "Gratuito com plano pago" seria inventar um termo que o mercado nao usa.
      "filtro.cobrancaRotulo.FREEMIUM",
      // "Tags" e a palavra usada em portugues tambem. "Etiquetas" existe no
      // dicionario e nao e como ninguem chama isso num catalogo de software.
      "ficha.tags",
    ]);

    const pt = new Map(achatar(textos("pt-BR")));
    for (const [chave, valorEn] of achatar(textos("en"))) {
      if (excecoes.has(chave)) continue;
      expect(pt.get(chave), `${chave} nao foi traduzido`).not.toBe(valorEn);
    }
  });
});
