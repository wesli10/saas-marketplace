import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { FormularioDeReenvio } from "@/src/components/reviews/FormularioDeReenvio";
import { ApiError } from "@/src/lib/api/client";
import { confirmarReview } from "@/src/lib/api/reviews";
import { textos } from "@/src/lib/i18n";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaHome, rotaProduto } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

type Resultado = "sucesso" | "expirado" | "indisponivel";

/**
 * A tela que o link do e-mail abre (spec 003 secao 6).
 *
 * A confirmacao acontece na renderizacao, e nao atras de um botao: quem clicou
 * no link ja disse o que queria dizer, e pedir um segundo clique so para
 * transformar o GET em POST trocaria uma sutileza tecnica por um passo a mais
 * para quem so quer terminar.
 *
 * Sao tres estados, e os dois ruins sao um so na pratica: token usado, expirado
 * e desconhecido respondem 410 iguais (AC-03), entao a tela oferece o reenvio
 * nos tres em vez de explicar qual foi o caso.
 */
export function paginaDeConfirmacao(idiomaDaRota: AppLocale) {
  return async function Pagina({
    params,
    searchParams,
  }: {
    params: Promise<{ locale: string }>;
    searchParams: Promise<Record<string, string | string[] | undefined>>;
  }) {
    const { locale } = await params;
    if (!ehLocale(locale) || locale !== idiomaDaRota) notFound();

    const query = await searchParams;
    const token = texto(query.token);
    const produto = texto(query.produto);
    const t = textos(idiomaDaRota);

    const resultado = await confirmar(token, idiomaDaRota);

    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        {resultado === "sucesso" ? (
          <section data-testid={TESTID.confirmacaoSucesso} role="status">
            <h1 className="text-3xl font-semibold tracking-tight">{t.confirmacao.tituloSucesso}</h1>
            <p className="mt-4 text-neutral-700">{t.confirmacao.sucesso}</p>
          </section>
        ) : null}

        {resultado === "expirado" ? (
          <section data-testid={TESTID.confirmacaoExpirado}>
            <h1 className="text-3xl font-semibold tracking-tight">
              {t.confirmacao.tituloExpirado}
            </h1>
            <p className="mt-4 text-neutral-700">{t.confirmacao.expirado}</p>

            {/* Sem o produto no link nao ha o que reenviar: o formulario pede o
                e-mail, e a API precisa dos dois. */}
            {produto ? <FormularioDeReenvio produto={produto} locale={idiomaDaRota} /> : null}
          </section>
        ) : null}

        {resultado === "indisponivel" ? (
          <section data-testid={TESTID.confirmacaoIndisponivel} role="alert">
            <h1 className="text-3xl font-semibold tracking-tight">
              {t.confirmacao.tituloIndisponivel}
            </h1>
            <p className="mt-4 text-neutral-700">{t.confirmacao.indisponivel}</p>
          </section>
        ) : null}

        <p className="mt-8">
          {produto ? (
            <Link
              href={rotaProduto(idiomaDaRota, produto)}
              data-testid={TESTID.confirmacaoVerProduto}
              className="underline"
            >
              {t.confirmacao.verProduto}
            </Link>
          ) : (
            <Link href={rotaHome(idiomaDaRota)} className="underline">
              {t.confirmacao.verCatalogo}
            </Link>
          )}
        </p>
      </main>
    );
  };
}

/**
 * Token ausente cai no mesmo estado do token invalido, e nao em 404: quem chega
 * aqui sem token clicou num link truncado pelo cliente de e-mail, e o que ele
 * precisa e do reenvio - nao de uma pagina de erro.
 */
async function confirmar(token: string, locale: AppLocale): Promise<Resultado> {
  if (!token) return "expirado";

  try {
    await confirmarReview(token, locale);
    return "sucesso";
  } catch (erro) {
    if (erro instanceof ApiError && erro.status === 410) return "expirado";
    if (erro instanceof ApiError && erro.status === 400) return "expirado";
    return "indisponivel";
  }
}

function texto(valor: string | string[] | undefined): string {
  if (Array.isArray(valor)) return valor[0] ?? "";
  return valor ?? "";
}

export function metadataDeConfirmacao(idiomaDaRota: AppLocale): Metadata {
  const t = textos(idiomaDaRota);

  return {
    title: t.confirmacao.tituloSucesso,
    // Tela transacional, com token na URL: nao ha nada aqui que um buscador
    // deva indexar, e o token nao pode acabar num indice publico.
    robots: { index: false, follow: false },
  };
}
