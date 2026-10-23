import { describe, expect, it } from "vitest";

import { dataFormatada, inicialDe, precoFormatado } from "@/src/lib/format";

/** Criterio de verificacao da tarefa 002-T16, parte pura. */
describe("formatacao", () => {
  it("preco muda com a moeda e com o idioma", () => {
    // O separador decimal e a posicao do simbolo mudam junto; e por isso que
    // isto nao e concatenacao de string.
    expect(precoFormatado("pt-BR", 135, "BRL")).toContain("135");
    expect(precoFormatado("pt-BR", 19.99, "USD")).toContain("19,99");
    expect(precoFormatado("en", 19.99, "USD")).toContain("19.99");
  });

  it("sem preco devolve null, e nao zero", () => {
    // Tres produtos do seed nao tem preco. "R$ 0,00" seria uma informacao
    // errada; a pagina decide o que dizer no lugar.
    expect(precoFormatado("pt-BR", null, "BRL")).toBeNull();
    expect(precoFormatado("pt-BR", 10, null)).toBeNull();
  });

  it("inicial serve de logo quando nao ha logo", () => {
    expect(inicialDe("Zendesk")).toBe("Z");
    expect(inicialDe("  octadesk")).toBe("O");
    expect(inicialDe("")).toBe("?");
  });

  it("data sai por extenso, em UTC", () => {
    // Sem `timeZone: UTC` a mesma data renderiza diferente no servidor e no
    // navegador do visitante, e o React acusa divergencia de hidratacao.
    expect(dataFormatada("pt-BR", "2026-08-27T03:00:00Z")).toContain("2026");
    expect(dataFormatada("pt-BR", null)).toBeNull();
    expect(dataFormatada("pt-BR", "nao-e-data")).toBeNull();
  });
});
