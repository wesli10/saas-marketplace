import { describe, expect, it } from "vitest";

import { comPagina, lerFiltro, paraQueryDaUrl } from "@/src/lib/filters";
import { paginacaoDe, SIZE_PADRAO } from "@/src/lib/paginacao";

/** Criterio de verificacao da tarefa 002-T19: a regra de paginacao. */
describe("paginacao", () => {
  it("divide o total pelo tamanho servido, e nao pelo padrao", () => {
    // O `size` vem da resposta, e nao do que a tela pediu: quem decide quantos
    // itens couberam e a API.
    const p = paginacaoDe({ page: 0, size: 5, total: 12 });

    expect(p.totalDePaginas).toBe(3);
    expect(p.anterior).toBeNull();
    expect(p.proxima).toBe(1);
  });

  it("nao oferece proxima quando o total fecha exatamente na ultima", () => {
    const p = paginacaoDe({ page: 1, size: 10, total: 20 });

    expect(p.totalDePaginas).toBe(2);
    expect(p.anterior).toBe(0);
    expect(p.proxima).toBeNull();
  });

  it("lista vazia continua sendo uma pagina, sem links", () => {
    const p = paginacaoDe({ page: 0, size: 12, total: 0 });

    expect(p.totalDePaginas).toBe(1);
    expect(p.anterior).toBeNull();
    expect(p.proxima).toBeNull();
  });

  it("pagina fora da faixa aponta para a ultima que existe", () => {
    // `?page=90` numa lista de 2 paginas: o link de voltar precisa levar a
    // algum lugar util, e nao para a pagina 89, tambem vazia.
    const p = paginacaoDe({ page: 90, size: 10, total: 20 });

    expect(p.anterior).toBe(1);
    expect(p.proxima).toBeNull();
  });

  it("usa o tamanho padrao quando a resposta nao informa nenhum", () => {
    const p = paginacaoDe({ total: SIZE_PADRAO + 1 });

    expect(p.pagina).toBe(0);
    expect(p.totalDePaginas).toBe(2);
  });
});

describe("o filtro noutra pagina", () => {
  it("preserva busca e filtros ao mudar de pagina", () => {
    const filtro = lerFiltro(
      new URLSearchParams("q=chatbot&integration=whatsapp&has_free_plan=true"),
    );

    const url = paraQueryDaUrl(comPagina(filtro, 2));

    expect(url.get("q")).toBe("chatbot");
    expect(url.getAll("integration")).toEqual(["whatsapp"]);
    expect(url.get("has_free_plan")).toBe("true");
    expect(url.get("page")).toBe("2");
  });

  it("a primeira pagina sai da URL em vez de virar page=0", () => {
    const filtro = lerFiltro(new URLSearchParams("has_free_plan=true&page=3"));

    expect(paraQueryDaUrl(comPagina(filtro, 0)).toString()).toBe("has_free_plan=true");
  });

  it("nao altera o filtro recebido", () => {
    const filtro = lerFiltro(new URLSearchParams("page=1"));

    comPagina(filtro, 5);

    expect(filtro.page).toBe("1");
  });
});
