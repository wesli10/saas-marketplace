import Link from "next/link";

import type { ArvoreDeCategorias, ListaDeIntegracoes } from "@/src/lib/api/catalog";
import { filtrosAplicados } from "@/src/lib/filtro-rotulos";
import { paraQueryDaUrl, semValor, type FiltroCatalogo } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { comQuery, rotaCategoria } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * Zero resultado nao e erro, e sim um beco - e beco sem saida e defeito de
 * interface (spec secao 5.2, AC-12).
 *
 * Entao a tela responde 200 e mostra tres coisas: o que foi filtrado, um jeito
 * de remover cada criterio um a um, e as categorias como alternativa. Cada
 * remocao e um `<a href>` para a mesma pagina com um criterio a menos, montado
 * por `semValor` - ou seja, sai da mesma funcao que os testes de unidade do T17
 * cobrem, e funciona sem JavaScript.
 */
export function ListaVazia({
  locale,
  base,
  filtro,
  integracoes,
  arvore,
}: {
  locale: AppLocale;
  base: string;
  filtro: FiltroCatalogo;
  integracoes: ListaDeIntegracoes;
  /** Buscada so quando a lista vem vazia: e a saida oferecida. */
  arvore: ArvoreDeCategorias | null;
}) {
  const t = textos(locale);
  const aplicados = filtrosAplicados(filtro, t, integracoes);
  const alternativas = (arvore?.items ?? []).flatMap((raiz) => raiz.children ?? []);

  const semEste = (campo: string, valor?: string) =>
    comQuery(
      base,
      paraQueryDaUrl(semValor(filtro, campo as Parameters<typeof semValor>[1], valor)),
    );

  return (
    <div className="mt-8" data-testid={TESTID.listaVazia}>
      <p className="text-lg">{aplicados.length > 0 ? t.vazio.comFiltro : t.lista.vazio}</p>

      {aplicados.length > 0 && (
        <div className="mt-4">
          <p className="text-sm text-neutral-500">{t.vazio.filtrosAplicados}</p>
          <ul className="mt-2 flex flex-wrap gap-2">
            {aplicados.map((aplicado) => (
              <li key={`${aplicado.campo}:${aplicado.valor ?? ""}`}>
                <Link
                  href={semEste(aplicado.campo, aplicado.valor)}
                  aria-label={t.vazio.remover(aplicado.rotulo)}
                  data-testid={TESTID.vazioRemoverFiltro}
                  data-campo={aplicado.campo}
                  className="inline-flex items-center gap-2 rounded-full border border-neutral-300 px-3 py-1 text-sm hover:border-neutral-500"
                >
                  {aplicado.rotulo}
                  <span aria-hidden="true">×</span>
                </Link>
              </li>
            ))}
          </ul>

          <Link
            href={base}
            data-testid={TESTID.vazioLimparTudo}
            className="mt-4 inline-block text-sm underline"
          >
            {t.filtro.limparTudo}
          </Link>
        </div>
      )}

      {alternativas.length > 0 && (
        <div className="mt-8">
          <p className="text-sm text-neutral-500">{t.vazio.alternativa}</p>
          <ul className="mt-2 flex flex-wrap gap-2">
            {alternativas.map((categoria) => (
              <li key={categoria.slug}>
                <Link
                  href={rotaCategoria(locale, categoria.slug ?? "")}
                  data-testid={TESTID.vazioCategoria}
                  data-slug={categoria.slug}
                  className="inline-block rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:border-neutral-500"
                >
                  {categoria.name}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
