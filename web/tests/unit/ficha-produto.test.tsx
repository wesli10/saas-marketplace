import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { FichaProduto } from "@/src/components/catalog/FichaProduto";
import type { ProdutoDetalhe } from "@/src/lib/api/catalog";

/**
 * Criterio de verificacao da tarefa 002-T22: AC-14.
 *
 * E teste de COMPONENTE, e nao e2e, por decisao registrada no plano (DT-10.4):
 * nenhum produto publicado pode estar sem traducao - o
 * `ProductPublicationValidator` do 001 barra -, entao o estado so existe se
 * alguem escrever via admin, e a suite e2e nao escreve. Aqui ele chega por
 * props, que e como a API o entregaria: 200 com os campos longos vazios.
 */
const BASE: ProdutoDetalhe = {
  slug: "acme",
  locale: "en",
  name: "Acme",
  tagline: "Automation for small teams",
  description: "A short description that comes from the basic record.",
  website_url: "https://acme.com",
  category: { slug: "agents-and-workflows", name: "Agents and workflows" },
  pricing_model: "SUBSCRIPTION",
  price_min: 19.99,
  price_currency: "USD",
  price_period: "MONTH",
  has_free_trial: true,
  has_free_plan: false,
  supported_languages: ["en"],
  hq_country: "US",
  integrations: [{ slug: "slack", name: "Slack" }],
  tags: [{ slug: "no-code", name: "No-code" }],
  pros: ["Cheap"],
  cons: ["English only"],
  best_for: "Small teams",
  faq: [{ question: "Is there a free plan?", answer: "No." }],
  updated_at: "2026-08-20T10:00:00Z",
};

const semCuradoria: ProdutoDetalhe = { ...BASE, pros: [], cons: [], best_for: "", faq: [] };

describe("ficha do produto", () => {
  it("renderiza os campos da spec secao 5.3", () => {
    render(<FichaProduto produto={BASE} locale="en" />);

    expect(screen.getByTestId("ficha-titulo")).toHaveTextContent("Acme");
    expect(screen.getByTestId("ficha-preco")).toHaveTextContent("$19.99");
    expect(screen.getByTestId("ficha-cobranca")).toHaveTextContent("Subscription");
    expect(screen.getAllByTestId("ficha-pro")).toHaveLength(1);
    expect(screen.getAllByTestId("ficha-con")).toHaveLength(1);
    expect(screen.getByTestId("ficha-faq-item")).toHaveTextContent("Is there a free plan?");
    // Codigo vira nome legivel pelo Intl, e nao por tabela nossa.
    expect(screen.getByTestId("ficha-idiomas")).toHaveTextContent("English");
    expect(screen.getByTestId("ficha-pais")).toHaveTextContent("United States");
    expect(screen.getByTestId("ficha-atualizado")).toHaveTextContent("August 20, 2026");
  });

  it("o link externo nao passa autoridade nem o window.opener", () => {
    render(<FichaProduto produto={BASE} locale="en" />);

    expect(screen.getByTestId("ficha-site")).toHaveAttribute("rel", "nofollow sponsored noopener");
    expect(screen.getByTestId("ficha-site")).toHaveAttribute("target", "_blank");
  });

  it("AC-14: sem conteudo curado, avisa e nao quebra", () => {
    render(<FichaProduto produto={semCuradoria} locale="en" />);

    expect(screen.getByTestId("ficha-traducao-ausente")).toBeInTheDocument();
    // O que existe continua na tela: o vazio e da curadoria, nao da ficha.
    expect(screen.getByTestId("ficha-titulo")).toHaveTextContent("Acme");
    expect(screen.getByTestId("ficha-preco")).toBeInTheDocument();
    expect(screen.queryByTestId("ficha-pro")).not.toBeInTheDocument();
    expect(screen.queryByTestId("ficha-faq-item")).not.toBeInTheDocument();
  });

  it("AC-14: o aviso leva a versao do outro idioma, no mesmo produto", () => {
    render(<FichaProduto produto={semCuradoria} locale="en" />);

    // Slug de produto nao muda com o idioma (spec 001 secao 4.2), so o segmento.
    expect(screen.getByTestId("ficha-outra-versao")).toHaveAttribute(
      "href",
      "/pt-BR/produtos/acme",
    );
  });

  it("produto com conteudo nao mostra o aviso", () => {
    render(<FichaProduto produto={BASE} locale="en" />);

    expect(screen.queryByTestId("ficha-traducao-ausente")).not.toBeInTheDocument();
  });

  it("sem preco publicado, mostra 'sob consulta' em vez de esconder o bloco", () => {
    render(
      <FichaProduto
        produto={{ ...BASE, price_min: undefined, price_currency: undefined }}
        locale="en"
      />,
    );

    expect(screen.getByTestId("ficha-preco")).toHaveTextContent("Price on request");
  });
});
