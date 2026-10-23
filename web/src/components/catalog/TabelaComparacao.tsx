import Link from "next/link";

import type { ProdutoDetalhe } from "@/src/lib/api/catalog";
import { nomeDoIdioma, nomeDoPais, precoFormatado } from "@/src/lib/format";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaProduto } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * Produtos em colunas, atributos em linhas (spec secao 5.4).
 *
 * A primeira coluna e `sticky` porque no celular a tabela rola na horizontal, e
 * sem ela quem rola para ver o terceiro produto perde de vista QUAL atributo
 * esta lendo - a tabela vira uma grade de valores soltos.
 *
 * A ordem das colunas e a ordem pedida na URL, e nao a que a API devolveu:
 * comparar e uma leitura lado a lado, e trocar a ordem no meio do caminho
 * confunde quem montou a comparacao.
 */
export function TabelaComparacao({
  produtos,
  locale,
  ordem,
}: {
  produtos: ProdutoDetalhe[];
  locale: AppLocale;
  ordem: readonly string[];
}) {
  const t = textos(locale);

  const emOrdem = ordem
    .map((slug) => produtos.find((produto) => produto.slug === slug))
    .filter((produto): produto is ProdutoDetalhe => produto !== undefined);

  const periodoDe = (produto: ProdutoDetalhe) =>
    produto.price_period === "YEAR"
      ? t.produto.porAno
      : produto.price_period === "ONE_TIME"
        ? t.produto.pagamentoUnico
        : t.produto.porMes;

  const linhas: { rotulo: string; celula: (produto: ProdutoDetalhe) => React.ReactNode }[] = [
    {
      rotulo: t.comparacao.atributo.preco,
      celula: (produto) => {
        const preco = precoFormatado(locale, produto.price_min, produto.price_currency);
        return preco ? t.produto.aPartirDe(preco, periodoDe(produto)) : t.produto.semPreco;
      },
    },
    {
      rotulo: t.comparacao.atributo.cobranca,
      celula: (produto) =>
        produto.pricing_model ? t.filtro.cobrancaRotulo[produto.pricing_model] : "—",
    },
    {
      rotulo: t.comparacao.atributo.testeGratis,
      celula: (produto) => (produto.has_free_trial ? t.comparacao.sim : t.comparacao.nao),
    },
    {
      rotulo: t.comparacao.atributo.planoGratuito,
      celula: (produto) => (produto.has_free_plan ? t.comparacao.sim : t.comparacao.nao),
    },
    { rotulo: t.comparacao.atributo.categoria, celula: (produto) => produto.category?.name ?? "—" },
    {
      rotulo: t.comparacao.atributo.idiomas,
      celula: (produto) =>
        (produto.supported_languages ?? [])
          .map((codigo) => nomeDoIdioma(locale, codigo))
          .join(", ") || "—",
    },
    {
      rotulo: t.comparacao.atributo.sede,
      celula: (produto) => (produto.hq_country ? nomeDoPais(locale, produto.hq_country) : "—"),
    },
    {
      rotulo: t.comparacao.atributo.integracoes,
      celula: (produto) => (produto.integrations ?? []).map((i) => i.name).join(", ") || "—",
    },
    { rotulo: t.comparacao.atributo.melhorPara, celula: (produto) => produto.best_for || "—" },
  ];

  return (
    // O scroll horizontal e do container, e nao da pagina: a constituicao do
    // artefato pede que o corpo nunca role na horizontal.
    <div className="mt-8 overflow-x-auto">
      <table data-testid={TESTID.comparacaoTabela} className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className="sticky left-0 z-10 bg-white p-3 text-left" />
            {emOrdem.map((produto) => (
              <th
                key={produto.slug}
                scope="col"
                data-testid={TESTID.comparacaoColuna}
                data-slug={produto.slug}
                className="min-w-48 border-b border-neutral-200 p-3 text-left align-bottom"
              >
                <Link
                  href={rotaProduto(locale, produto.slug ?? "")}
                  className="font-medium underline"
                >
                  {produto.name}
                </Link>
                <span className="mt-1 block font-normal text-neutral-600">{produto.tagline}</span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha) => (
            <tr key={linha.rotulo} data-testid={TESTID.comparacaoLinha} className="align-top">
              <th
                scope="row"
                // Fixa ao rolar na horizontal: sem isso, no celular, o valor da
                // terceira coluna aparece sem o nome do atributo ao lado.
                className="sticky left-0 z-10 border-b border-neutral-200 bg-white p-3 text-left font-medium whitespace-nowrap"
              >
                {linha.rotulo}
              </th>
              {emOrdem.map((produto) => (
                <td key={produto.slug} className="border-b border-neutral-200 p-3">
                  {linha.celula(produto)}
                </td>
              ))}
            </tr>
          ))}
          <tr data-testid={TESTID.comparacaoLinha} className="align-top">
            <th
              scope="row"
              className="sticky left-0 z-10 bg-white p-3 text-left font-medium whitespace-nowrap"
            >
              {t.comparacao.atributo.site}
            </th>
            {emOrdem.map((produto) => (
              <td key={produto.slug} className="p-3">
                {produto.website_url ? (
                  <a
                    href={produto.website_url}
                    target="_blank"
                    rel="nofollow sponsored noopener"
                    data-testid={TESTID.fichaSite}
                    data-slug={produto.slug}
                    className="underline"
                  >
                    {t.ficha.visitarSite}
                  </a>
                ) : (
                  "—"
                )}
              </td>
            ))}
          </tr>
        </tbody>
      </table>
    </div>
  );
}
