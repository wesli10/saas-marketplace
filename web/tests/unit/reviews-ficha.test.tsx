import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BlocoDeReviews } from "@/src/components/reviews/BlocoDeReviews";
import { NotaResumo } from "@/src/components/reviews/NotaResumo";
import { ReviewCard } from "@/src/components/reviews/ReviewCard";
import type { ListaDeReviews, Review } from "@/src/lib/api/reviews";

/**
 * Criterio de verificacao da tarefa 003-T11: AC-15 e AC-19.
 *
 * Teste de COMPONENTE para os estados que dependem do dado, e nao da tela: o
 * seed nao tem review nenhuma (risco R-01 - seed com review seria dado
 * inventado), entao "card sem selo" e "review no outro idioma" so existem se
 * chegarem por props. O fluxo de ponta a ponta e o e2e da suite de escrita.
 */
const VERIFICADA: Review = {
  id: "01998a4c-6f3e-7c21-9a10-2f6c1d4e8b03",
  rating: 5,
  would_recommend: true,
  title: "Resolveu o atendimento",
  body: "Usamos ha oito meses e o tempo de primeira resposta caiu pela metade.",
  locale: "pt-BR",
  author_name: "Ana Ribeiro",
  author_domain: "acme.com.br",
  verified: true,
  published_at: "2026-08-20T10:00:00Z",
};

const SEM_SELO: Review = {
  ...VERIFICADA,
  id: "01998a4c-6f3e-7c21-9a10-2f6c1d4e8b04",
  author_domain: "gmail.com",
  verified: false,
};

const EM_INGLES: Review = {
  ...VERIFICADA,
  id: "01998a4c-6f3e-7c21-9a10-2f6c1d4e8b05",
  locale: "en",
  title: "Solved our support queue",
  body: "We moved three inboxes into it and first response time dropped by half.",
};

describe("card de review", () => {
  it("mostra o selo com o dominio quando a review e verificada", () => {
    render(<ReviewCard review={VERIFICADA} locale="pt-BR" />);

    expect(screen.getByTestId("review-selo")).toHaveTextContent("acme.com.br");
    expect(screen.getByTestId("review-nota")).toHaveTextContent("5/5");
  });

  it("card sem `verified` nao mostra o selo - nem o dominio", () => {
    render(<ReviewCard review={SEM_SELO} locale="pt-BR" />);

    expect(screen.queryByTestId("review-selo")).not.toBeInTheDocument();
    // Dominio gratuito nao vira rotulo de segunda classe: ele simplesmente nao
    // aparece (spec secao 4.5).
    expect(screen.queryByText(/gmail\.com/)).not.toBeInTheDocument();
  });

  it("AC-15: review em `en` numa ficha em pt-BR aparece marcada como escrita em ingles", () => {
    render(<ReviewCard review={EM_INGLES} locale="pt-BR" />);

    const marca = screen.getByTestId("review-idioma");
    expect(marca).toHaveAttribute("data-idioma", "en");
    // Assercao sobre o texto porque e o proprio texto que esta sendo verificado.
    expect(marca).toHaveTextContent(/ingl/i);

    // A marcacao nao e so visual: com `lang`, o leitor de tela troca de
    // pronuncia em vez de ler ingles com fonemas de portugues.
    expect(screen.getByTestId("review-corpo")).toHaveAttribute("lang", "en");
  });

  it("review no mesmo idioma da pagina nao ganha marcacao nenhuma", () => {
    render(<ReviewCard review={VERIFICADA} locale="pt-BR" />);

    expect(screen.queryByTestId("review-idioma")).not.toBeInTheDocument();
  });
});

describe("resumo da nota", () => {
  it("AC-19: mostra media, contagem e quantas sao verificadas", () => {
    render(
      <NotaResumo
        nota={{ average: 4.3, count: 3, recommend_ratio: 0.67, verified_count: 2 }}
        locale="pt-BR"
      />,
    );

    expect(screen.getByTestId("reviews-media")).toHaveAttribute("data-media", "4.3");
    // Separador decimal do idioma, pelo Intl - e nao tabela nossa.
    expect(screen.getByTestId("reviews-media")).toHaveTextContent("4,3");
    expect(screen.getByTestId("reviews-contagem")).toHaveTextContent("3 avaliações");
    expect(screen.getByTestId("reviews-verificadas")).toHaveTextContent("2");
  });

  it("a mesma nota sai com ponto decimal em ingles", () => {
    render(
      <NotaResumo
        nota={{ average: 4.3, count: 3, recommend_ratio: 0.67, verified_count: 2 }}
        locale="en"
      />,
    );

    expect(screen.getByTestId("reviews-media")).toHaveTextContent("4.3");
  });

  it("produto sem avaliacao diz isso, e nao mostra zero", () => {
    render(<NotaResumo nota={null} locale="pt-BR" />);

    expect(screen.getByTestId("reviews-vazio")).toBeInTheDocument();
    expect(screen.queryByTestId("reviews-media")).not.toBeInTheDocument();
    expect(screen.queryByText("0,0")).not.toBeInTheDocument();
  });
});

describe("bloco de reviews", () => {
  const lista: ListaDeReviews = {
    rating: { average: 4.3, count: 3, recommend_ratio: 0.67, verified_count: 2 },
    items: [VERIFICADA, EM_INGLES],
    page: 0,
    size: 5,
    total: 2,
  };

  it("lista as avaliacoes e aponta para a politica no idioma da pagina", () => {
    render(<BlocoDeReviews lista={lista} locale="pt-BR" base="/pt-BR/produtos/acme" slug="acme" />);

    expect(screen.getAllByTestId("review-card")).toHaveLength(2);
    expect(screen.getByTestId("reviews-politica")).toHaveAttribute(
      "href",
      "/pt-BR/politica-de-reviews",
    );
  });

  it("a politica muda de rota com o idioma", () => {
    render(<BlocoDeReviews lista={lista} locale="en" base="/en/products/acme" slug="acme" />);

    expect(screen.getByTestId("reviews-politica")).toHaveAttribute("href", "/en/review-policy");
  });

  it("falha na API vira aviso no bloco, e nao ficha sem avaliacoes", () => {
    render(<BlocoDeReviews lista={null} locale="pt-BR" base="/pt-BR/produtos/acme" slug="acme" />);

    expect(screen.getByTestId("reviews-indisponivel")).toBeInTheDocument();
    // O que nao pode acontecer e sumir em silencio: sumir seria
    // indistinguivel de "ninguem avaliou".
    expect(screen.queryByTestId("reviews-vazio")).not.toBeInTheDocument();
  });

  it("uma pagina so nao mostra paginacao", () => {
    render(<BlocoDeReviews lista={lista} locale="pt-BR" base="/pt-BR/produtos/acme" slug="acme" />);

    expect(screen.queryByTestId("reviews-paginacao")).not.toBeInTheDocument();
  });

  it("com mais de uma pagina, o link leva a proxima pela URL", () => {
    render(
      <BlocoDeReviews
        lista={{ ...lista, total: 12 }}
        locale="pt-BR"
        base="/pt-BR/produtos/acme"
        slug="acme"
      />,
    );

    expect(screen.getByTestId("reviews-paginacao-proxima")).toHaveAttribute(
      "href",
      "/pt-BR/produtos/acme?avaliacoes=1",
    );
  });
});
