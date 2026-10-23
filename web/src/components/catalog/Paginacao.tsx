import Link from "next/link";

import { comPagina, paraQueryDaUrl, type FiltroCatalogo } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { paginacaoDe, type FatiaDaApi } from "@/src/lib/paginacao";
import { comQuery } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * Paginacao como link de verdade (spec secao 5.2).
 *
 * Nao ha botao nem handler: sao dois `<a href>` com `rel="prev"`/`rel="next"`,
 * entao o robo de busca segue e o visitante sem JavaScript navega pelo
 * mecanismo do navegador - o mesmo motivo do formulario GET no filtro. Como
 * cada pagina e uma URL, o botao "voltar" do navegador devolve a lista
 * anterior, com os filtros que ela tinha (AC-10).
 *
 * O href sai de `filtro` - o mesmo objeto que a pagina leu da URL -, e nao de
 * uma query montada a parte: assim paginar preserva filtro e busca sem que
 * ninguem precise lembrar de copiar cada parametro.
 */
export function Paginacao({
  locale,
  base,
  filtro,
  fatia,
}: {
  locale: AppLocale;
  base: string;
  filtro: FiltroCatalogo;
  fatia: FatiaDaApi;
}) {
  const t = textos(locale);
  const { pagina, totalDePaginas, anterior, proxima } = paginacaoDe(fatia);

  // Uma pagina so nao e paginacao: navegacao que nao leva a lugar nenhum e
  // ruido para quem le a tela e para quem le o HTML.
  if (totalDePaginas <= 1) return null;

  const href = (destino: number) => comQuery(base, paraQueryDaUrl(comPagina(filtro, destino)));

  return (
    <nav
      aria-label={t.paginacao.navegacao}
      data-testid={TESTID.paginacao}
      className="mt-8 flex items-center justify-between gap-4 border-t border-neutral-200 pt-4"
    >
      {anterior === null ? (
        <span />
      ) : (
        <Link
          href={href(anterior)}
          rel="prev"
          data-testid={TESTID.paginacaoAnterior}
          className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:border-neutral-500"
        >
          {t.paginacao.anterior}
        </Link>
      )}

      <span className="text-sm text-neutral-500" data-testid={TESTID.paginacaoStatus}>
        {/* Rotulo em base 1, URL em base 0: ver o cabecalho de `paginacao.ts`. */}
        {t.paginacao.status(Math.min(pagina, totalDePaginas - 1) + 1, totalDePaginas)}
      </span>

      {proxima === null ? (
        <span />
      ) : (
        <Link
          href={href(proxima)}
          rel="next"
          data-testid={TESTID.paginacaoProxima}
          className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:border-neutral-500"
        >
          {t.paginacao.proxima}
        </Link>
      )}
    </nav>
  );
}
