import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, ApiIndisponivelError, buscarNaApi } from "@/src/lib/api/client";

/** Criterio de verificacao da tarefa 002-T12. */
describe("cliente da API", () => {
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function responderCom(corpo: unknown, status = 200) {
    const espia = vi.fn(
      async () =>
        new Response(JSON.stringify(corpo), {
          status,
          headers: {
            "content-type": status >= 400 ? "application/problem+json" : "application/json",
          },
        }),
    );
    globalThis.fetch = espia as unknown as typeof fetch;
    return espia;
  }

  it("200 devolve o corpo e manda o idioma no cabecalho", async () => {
    const espia = responderCom({ locale: "en", items: [] });

    const corpo = await buscarNaApi<{ locale: string }>("/api/v1/categories", { locale: "en" });

    expect(corpo.locale).toBe("en");
    const chamada = espia.mock.calls[0] as unknown as [URL, RequestInit];
    expect(chamada[1].headers).toMatchObject({ "Accept-Language": "en" });
  });

  it("lista vira parametro repetido, e nao lista separada por virgula", async () => {
    const espia = responderCom({ items: [] });

    await buscarNaApi("/api/v1/products", {
      locale: "pt-BR",
      query: { integration: ["whatsapp", "slack"], has_free_plan: true, q: undefined },
    });

    const chamada = espia.mock.calls[0] as unknown as [URL, RequestInit];
    const url = chamada[0].search;
    expect(url).toContain("integration=whatsapp");
    expect(url).toContain("integration=slack");
    expect(url).toContain("has_free_plan=true");
    // Parametro ausente nao vira "q=undefined" na query.
    expect(url).not.toContain("q=");
  });

  it("404 vira ApiError com o Problem Details lido", async () => {
    responderCom(
      { status: 404, title: "Recurso nao encontrado", missing_slugs: ["nao-existe"] },
      404,
    );

    const erro = await buscarNaApi("/api/v1/products/x", { locale: "pt-BR" }).catch((e) => e);

    expect(erro).toBeInstanceOf(ApiError);
    expect((erro as ApiError).status).toBe(404);
    expect((erro as ApiError).problema?.missing_slugs).toEqual(["nao-existe"]);
  });

  it("400 tambem vira ApiError, com os campos em errors[]", async () => {
    responderCom({ status: 400, errors: [{ field: "pricing_model" }] }, 400);

    const erro = (await buscarNaApi("/api/v1/products", { locale: "pt-BR" }).catch(
      (e) => e,
    )) as ApiError;

    expect(erro.status).toBe(400);
    expect(erro.problema?.errors?.[0]?.field).toBe("pricing_model");
  });

  it("5xx sem corpo JSON nao vira uma segunda excecao", async () => {
    globalThis.fetch = vi.fn(
      async () => new Response("<html>erro</html>", { status: 502 }),
    ) as unknown as typeof fetch;

    const erro = (await buscarNaApi("/api/v1/categories", { locale: "pt-BR" }).catch(
      (e) => e,
    )) as ApiError;

    expect(erro).toBeInstanceOf(ApiError);
    expect(erro.status).toBe(502);
    expect(erro.problema).toBeNull();
  });

  it("timeout aborta a requisicao em vez de pendurar a pagina", async () => {
    // A API que nunca responde e pior que a que responde erro: sem o abort, a
    // renderizacao fica presa e o visitante ve um spinner eterno.
    globalThis.fetch = vi.fn(
      (_url, opcoes) =>
        new Promise((_, rejeitar) => {
          (opcoes as RequestInit).signal?.addEventListener("abort", () =>
            rejeitar(new DOMException("aborted", "AbortError")),
          );
        }),
    ) as unknown as typeof fetch;

    const promessa = buscarNaApi("/api/v1/categories", { locale: "pt-BR" }).catch((e) => e);
    await vi.advanceTimersByTimeAsync(6000);

    expect(await promessa).toBeInstanceOf(ApiIndisponivelError);
  });

  it("API fora do ar e um erro diferente de 5xx", async () => {
    globalThis.fetch = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;

    const erro = await buscarNaApi("/api/v1/categories", { locale: "pt-BR" }).catch((e) => e);

    // Distinguir os dois importa: 5xx a API respondeu e esta viva; aqui nao ha
    // ninguem do outro lado, e a pagina de erro pode dizer coisas diferentes.
    expect(erro).toBeInstanceOf(ApiIndisponivelError);
  });
});
