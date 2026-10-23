import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import NaoEncontrado from "@/app/[locale]/not-found";

/**
 * Criterio de verificacao da tarefa 002-T14, parte de renderizacao.
 *
 * O `not-found.tsx` do Next nao recebe params, entao ele tira o idioma do
 * caminho. E isso que este teste exercita - e de quebra mantem jsdom, Testing
 * Library e jest-dom na cadeia do `verify`.
 */
const caminho = vi.hoisted(() => ({ atual: "/pt-BR/produtos/x" }));
vi.mock("next/navigation", () => ({ usePathname: () => caminho.atual }));

describe("pagina de nao encontrado", () => {
  it("fala portugues quando a rota e pt-BR", () => {
    caminho.atual = "/pt-BR/produtos/inexistente";
    render(<NaoEncontrado />);

    expect(screen.getByTestId("pagina-nao-encontrada")).toBeInTheDocument();
    expect(screen.getByTestId("link-home")).toHaveAttribute("href", "/pt-BR");
  });

  it("fala ingles quando a rota e en", () => {
    caminho.atual = "/en/products/missing";
    render(<NaoEncontrado />);

    expect(screen.getByTestId("link-home")).toHaveAttribute("href", "/en");
    // A asserção e sobre o texto, entao getByText e o seletor certo aqui.
    expect(screen.getByText("We could not find this page")).toBeInTheDocument();
  });

  it("caminho sem idioma valido cai no padrao, em vez de quebrar", () => {
    caminho.atual = "/alguma-coisa";
    render(<NaoEncontrado />);

    expect(screen.getByTestId("link-home")).toHaveAttribute("href", "/pt-BR");
  });
});
