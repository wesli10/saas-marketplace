import Link from "next/link";

import { FiltroPainel } from "@/src/components/catalog/FiltroPainel";
import { ListaVazia } from "@/src/components/catalog/ListaVazia";
import { Paginacao } from "@/src/components/catalog/Paginacao";
import { ProdutoCard } from "@/src/components/catalog/ProdutoCard";
import { arvoreDeCategorias } from "@/src/lib/api/catalog";
import {
  comSelecaoAlternada,
  MAXIMO_PARA_COMPARAR,
  MINIMO_PARA_COMPARAR,
  semSelecao,
} from "@/src/lib/comparacao";
import type { ListaDeIntegracoes, ListaDeProdutos } from "@/src/lib/api/catalog";
import type { FiltroCatalogo } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaComparacao } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * A lista de produtos com os filtros e a paginacao - a mesma tela por duas
 * portas de entrada (spec secao 5.2): categoria e busca.
 *
 * Duas copias divergiriam na primeira correcao, e a que ninguem lembrasse de
 * ajustar seria justamente a menos visitada. O que muda entre as duas portas e
 * so o cabecalho e o `base`, entao e isso que fica na pagina.
 */
export async function ListaComFiltros({
  locale,
  base,
  filtro,
  lista,
  integracoes,
}: {
  locale: AppLocale;
  /** A rota da propria pagina: o `action` do filtro e a base dos links de pagina. */
  base: string;
  filtro: FiltroCatalogo;
  lista: ListaDeProdutos;
  integracoes: ListaDeIntegracoes;
}) {
  const t = textos(locale);
  const produtos = lista.items ?? [];

  // A arvore so e buscada quando nao ha o que mostrar: e a saida do estado
  // vazio (AC-12), e pagar por ela em toda lista cheia seria uma ida a API por
  // nada. Fica aqui, e nao em cada pagina, para as duas portas de entrada
  // ganharem a mesma saida sem ninguem precisar lembrar.
  //
  // Falha nao vira pagina de erro: a lista principal ja respondeu, e derrubar
  // uma tela inteira porque a *sugestao* nao carregou seria trocar um vazio com
  // saida por um erro sem nenhuma. Sem a arvore, o vazio mostra os filtros e o
  // "limpar", que e o essencial do AC-12.
  const arvore = produtos.length === 0 ? await arvoreDeCategorias(locale).catch(() => null) : null;

  return (
    <div className="mt-8 grid gap-10 md:grid-cols-[16rem_1fr]">
      <aside>
        <FiltroPainel locale={locale} acao={base} filtro={filtro} integracoes={integracoes} />
      </aside>

      <section>
        <p className="text-sm text-neutral-500" data-testid={TESTID.listaContagem}>
          {t.lista.resultados(lista.total ?? 0)}
        </p>

        {filtro.comparar.length > 0 && (
          <div
            data-testid={TESTID.compararBarra}
            className="mt-3 flex flex-wrap items-center gap-4 rounded-md border border-neutral-200 px-3 py-2 text-sm"
          >
            <span>{t.comparacao.selecionados(filtro.comparar.length)}</span>
            {filtro.comparar.length >= MINIMO_PARA_COMPARAR && (
              <Link
                href={rotaComparacao(locale, filtro.comparar)}
                data-testid={TESTID.compararIr}
                className="font-medium underline"
              >
                {t.comparacao.ir}
              </Link>
            )}
            <Link
              href={semSelecao(base, filtro)}
              data-testid={TESTID.compararLimpar}
              replace
              className="underline"
            >
              {t.comparacao.limpar}
            </Link>
          </div>
        )}

        {produtos.length === 0 ? (
          <ListaVazia
            locale={locale}
            base={base}
            filtro={filtro}
            integracoes={integracoes}
            arvore={arvore}
          />
        ) : (
          <ul className="mt-4 grid gap-4 sm:grid-cols-2">
            {produtos.map((produto) => {
              const slug = produto.slug ?? "";
              const selecionado = filtro.comparar.includes(slug);
              return (
                <ProdutoCard
                  key={produto.slug}
                  produto={produto}
                  locale={locale}
                  comparacao={{
                    href: comSelecaoAlternada(base, filtro, slug),
                    selecionado,
                    // Passar do limite nao empurra o mais antigo para fora:
                    // remover em silencio o que a pessoa escolheu e pior do que
                    // nao aceitar o quarto.
                    desabilitado: !selecionado && filtro.comparar.length >= MAXIMO_PARA_COMPARAR,
                  }}
                />
              );
            })}
          </ul>
        )}

        {/* Fora do `else`: numa pagina fora da faixa a lista vem vazia, e e
            justamente ai que o link de voltar precisa aparecer. */}
        <Paginacao locale={locale} base={base} filtro={filtro} fatia={lista} />
      </section>
    </div>
  );
}
