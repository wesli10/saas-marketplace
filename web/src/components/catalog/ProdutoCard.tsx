import Link from "next/link";

import type { ProdutoResumo } from "@/src/lib/api/catalog";
import { inicialDe, precoFormatado } from "@/src/lib/format";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { MAXIMO_PARA_COMPARAR } from "@/src/lib/comparacao";
import { rotaProduto } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * Card de produto.
 *
 * O `data-testid` e de tipo, e o `data-slug` diz qual - um id por produto
 * amarraria a suite aos 20 produtos do seed (constituicao secao 6).
 *
 * O bloco do logo tem tamanho fixo mesmo sem imagem. Hoje **nenhum** produto do
 * seed tem `logo_url`, entao o fallback e o caminho normal, e nao a excecao:
 * se ele encolhesse, a lista inteira mudaria de altura no dia em que o primeiro
 * logo aparecer.
 */
export function ProdutoCard({
  produto,
  locale,
  comparacao,
}: {
  produto: ProdutoResumo;
  locale: AppLocale;
  /**
   * Quando a lista sabe montar a selecao, o card ganha o controle de comparar.
   * E um `<a>` para a propria lista com a selecao trocada - sem handler, sem
   * estado de cliente: recarregar mantem o que estava marcado.
   */
  comparacao?: { href: string; selecionado: boolean; desabilitado: boolean };
}) {
  const t = textos(locale);
  const preco = precoFormatado(locale, produto.price_min, produto.price_currency);
  const periodo =
    produto.price_period === "YEAR"
      ? t.produto.porAno
      : produto.price_period === "ONE_TIME"
        ? t.produto.pagamentoUnico
        : t.produto.porMes;

  return (
    // O `li` e quem ocupa a altura da celula da grade, e nao o link do card:
    // com `h-full` no link, ele cobria o controle de comparar logo abaixo e o
    // clique caia no card (encontrado pelo e2e do T24, e invisivel a olho nu).
    <li className="flex h-full flex-col">
      <Link
        href={rotaProduto(locale, produto.slug ?? "")}
        data-testid={TESTID.produtoCard}
        data-slug={produto.slug}
        className="flex flex-1 gap-4 rounded-lg border border-neutral-200 p-4 transition hover:border-neutral-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
      >
        <span
          data-testid={TESTID.produtoLogo}
          aria-hidden="true"
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-neutral-100 text-lg font-semibold text-neutral-500"
        >
          {inicialDe(produto.name ?? "")}
        </span>

        <span className="flex min-w-0 flex-col">
          <span className="font-medium">{produto.name}</span>
          <span className="mt-0.5 text-sm text-neutral-600">{produto.tagline}</span>

          <span className="mt-2 text-sm" data-testid={TESTID.produtoPreco}>
            {preco ? t.produto.aPartirDe(preco, periodo) : t.produto.semPreco}
          </span>

          {(produto.has_free_trial || produto.has_free_plan) && (
            <span className="mt-2 flex flex-wrap gap-1.5">
              {produto.has_free_plan && <Badge>{t.produto.planoGratuito}</Badge>}
              {produto.has_free_trial && <Badge>{t.produto.testeGratis}</Badge>}
            </span>
          )}
        </span>
      </Link>

      {comparacao ? (
        comparacao.desabilitado ? (
          <span
            data-testid={TESTID.compararLimite}
            data-slug={produto.slug}
            className="mt-2 inline-block text-sm text-neutral-400"
          >
            {t.comparacao.limite(MAXIMO_PARA_COMPARAR)}
          </span>
        ) : (
          <Link
            href={comparacao.href}
            data-testid={TESTID.compararControle}
            data-slug={produto.slug}
            data-selecionado={comparacao.selecionado ? "sim" : "nao"}
            // `replace`: marcar e desmarcar tres caixas nao e navegacao, e nao
            // pode encher o historico (mesma decisao do filtro, no T17).
            replace
            className="mt-2 inline-block text-sm underline"
          >
            {comparacao.selecionado ? t.comparacao.remover : t.comparacao.adicionar}
          </Link>
        )
      ) : null}
    </li>
  );
}

function Badge({ children }: { children: React.ReactNode }) {
  return (
    <span
      data-testid={TESTID.produtoBadge}
      className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-700"
    >
      {children}
    </span>
  );
}
