import { describe, expect, it } from "vitest";

import { idiomaPreferido } from "@/src/lib/i18n/accept-language";

/** Criterio de verificacao da tarefa 002-T10, parte pura. */
describe("negociacao de idioma", () => {
  it("sem cabecalho cai no padrao", () => {
    expect(idiomaPreferido(null)).toBe("pt-BR");
    expect(idiomaPreferido("")).toBe("pt-BR");
  });

  it("respeita o idioma pedido", () => {
    expect(idiomaPreferido("en")).toBe("en");
    expect(idiomaPreferido("en-US,en;q=0.9")).toBe("en");
    expect(idiomaPreferido("pt-BR,pt;q=0.9")).toBe("pt-BR");
  });

  it("casa pela subtag primaria", () => {
    // Quem pede portugues de Portugal recebe portugues, e nao ingles.
    expect(idiomaPreferido("pt-PT")).toBe("pt-BR");
    expect(idiomaPreferido("en-GB")).toBe("en");
  });

  it("ordena por q, e nao pela posicao", () => {
    expect(idiomaPreferido("pt-BR;q=0.2,en;q=0.9")).toBe("en");
    expect(idiomaPreferido("en;q=0.3,pt-BR;q=0.8")).toBe("pt-BR");
  });

  it("idioma desconhecido nao e erro: cai no padrao", () => {
    expect(idiomaPreferido("fr-FR,fr;q=0.9")).toBe("pt-BR");
    expect(idiomaPreferido("ja,ko;q=0.7")).toBe("pt-BR");
  });

  it("q=0 significa recusa, e nao indiferenca", () => {
    // Quem diz `en;q=0` esta pedindo para NAO receber ingles.
    expect(idiomaPreferido("en;q=0,pt-BR;q=0.5")).toBe("pt-BR");
    expect(idiomaPreferido("pt-BR;q=0")).toBe("pt-BR");
  });

  it("curinga vai para o padrao", () => {
    expect(idiomaPreferido("*")).toBe("pt-BR");
  });
});
