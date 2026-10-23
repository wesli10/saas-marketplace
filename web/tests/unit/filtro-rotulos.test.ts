import { describe, expect, it } from "vitest";

import { filtrosAplicados } from "@/src/lib/filtro-rotulos";
import { lerFiltro } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";

const INTEGRACOES = {
  items: [
    { slug: "whatsapp", name: "WhatsApp" },
    { slug: "slack", name: "Slack" },
  ],
};

/** Criterio de verificacao da tarefa 002-T21: o que o estado vazio lista. */
describe("filtros aplicados, em texto", () => {
  it("nomeia a integracao pelo catalogo, e nao pelo slug", () => {
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("integration=whatsapp")),
      textos("pt-BR"),
      INTEGRACOES,
    );

    expect(aplicados).toEqual([{ campo: "integration", valor: "whatsapp", rotulo: "WhatsApp" }]);
  });

  it("cai no slug quando a integracao nao esta mais no catalogo", () => {
    // Sem isto, um filtro por integracao removida do seed ficaria sem rotulo -
    // e sem rotulo nao ha como remove-lo.
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("integration=sumiu")),
      textos("pt-BR"),
      INTEGRACOES,
    );

    expect(aplicados[0]?.rotulo).toBe("sumiu");
  });

  it("traduz o modelo de cobranca, e devolve o valor cru quando nao conhece", () => {
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("pricing_model=FREEMIUM&pricing_model=NAO_EXISTE")),
      textos("en"),
      INTEGRACOES,
    );

    expect(aplicados.map((a) => a.rotulo)).toEqual(["Freemium", "NAO_EXISTE"]);
  });

  it("lista um item por valor, e nao um por campo", () => {
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("integration=whatsapp&integration=slack")),
      textos("pt-BR"),
      INTEGRACOES,
    );

    expect(aplicados).toHaveLength(2);
  });

  it("paginacao nao e filtro", () => {
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("page=2&size=5")),
      textos("pt-BR"),
      INTEGRACOES,
    );

    expect(aplicados).toEqual([]);
  });

  it("traz busca, gratuidade e preco com o texto do idioma", () => {
    const aplicados = filtrosAplicados(
      lerFiltro(new URLSearchParams("q=chatbot&has_free_plan=true&price_max=200")),
      textos("pt-BR"),
      INTEGRACOES,
    );

    expect(aplicados.map((a) => a.campo)).toEqual(["q", "has_free_plan", "price_max"]);
    expect(aplicados[0]?.rotulo).toContain("chatbot");
    // O rotulo e o mesmo do controle no painel ("Tem plano gratuito"): dois
    // textos para o mesmo criterio fariam a remocao parecer outro filtro.
    expect(aplicados[1]?.rotulo).toBe("Tem plano gratuito");
    expect(aplicados[2]?.rotulo).toBe("Preço até 200");
  });
});
