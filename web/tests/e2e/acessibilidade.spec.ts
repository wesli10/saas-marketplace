import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

/**
 * Criterio de verificacao da tarefa 002-T27: AC-20.
 *
 * Fora da smoke de proposito: axe carrega e roda um motor de analise em cada
 * pagina, e o orcamento da smoke e de 5 minutos para cinco casos. Aqui ele roda
 * na suite completa, que e onde o custo cabe.
 *
 * O corte e **critical e serious**. Violacao "moderate"/"minor" do axe inclui
 * muita heuristica de contraste e de landmark que gera ruido; travar o build
 * nelas treina quem le a ignorar o relatorio inteiro. O que nao passa aqui e o
 * que quebra o uso: campo sem rotulo, contraste ilegivel, ordem de cabecalho
 * quebrada, ARIA invalido.
 */
const PAGINAS: [string, string][] = [
  ["home", "/pt-BR"],
  ["categoria", "/pt-BR/categorias/agentes-e-workflows"],
  ["ficha", "/pt-BR/produtos/zapier"],
  ["categoria em ingles", "/en/categories/agents-and-workflows"],
  ["busca", "/pt-BR/buscar?q=zapier"],
  ["comparacao", "/pt-BR/comparar?slugs=zapier,make"],
  // Telas da spec 003. A ficha ja esta na lista acima e agora carrega o bloco
  // de avaliacoes; o formulario e a politica entram aqui porque sao leitura
  // pura. As telas que dependem de uma avaliacao publicada - e a de
  // confirmacao, que escreve - tem axe proprio na suite de escrita.
  ["formulario de avaliacao", "/pt-BR/produtos/zapier/avaliar"],
  ["politica de reviews", "/pt-BR/politica-de-reviews"],
  ["politica de reviews em ingles", "/en/review-policy"],
];

for (const [nome, rota] of PAGINAS) {
  test(`axe nao acusa violacao critica nem seria: ${nome}`, async ({ page }) => {
    await page.goto(rota);

    const resultado = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();

    const graves = resultado.violations.filter(
      (violacao) => violacao.impact === "critical" || violacao.impact === "serious",
    );

    // A mensagem lista regra e seletor: relatorio que so diz "falhou" faz
    // alguem abrir o navegador para descobrir o que ja se sabia.
    const descricao = graves
      .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)
      .join("\n");

    expect(graves, descricao).toHaveLength(0);
  });
}
