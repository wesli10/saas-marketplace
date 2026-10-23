import { clonar, paraQueryDaUrl, type FiltroCatalogo } from "@/src/lib/filters";
import { comQuery } from "@/src/lib/routes";

/**
 * A selecao para comparar, acumulada na URL (spec secao 5.2).
 *
 * Os limites sao os da API (`ProductController`: de 2 a 3 slugs). Repetidos
 * aqui porque a tela precisa deles ANTES de chamar - o AC-16 pede que faixa
 * invalida vire mensagem, e nao um 400 exibido como erro. Quando divergirem,
 * quem manda e a API: e ela que responde 400, e a tela so evita o passeio.
 */
export const MINIMO_PARA_COMPARAR = 2;
export const MAXIMO_PARA_COMPARAR = 3;

/** O parametro da rota de comparacao e uma lista separada por virgula. */
export function lerSlugs(valor: string | string[] | undefined): string[] {
  const bruto = Array.isArray(valor) ? valor.join(",") : (valor ?? "");
  return bruto
    .split(",")
    .map((slug) => slug.trim())
    .filter((slug) => slug !== "");
}

export function faixaValida(slugs: readonly string[]): boolean {
  return slugs.length >= MINIMO_PARA_COMPARAR && slugs.length <= MAXIMO_PARA_COMPARAR;
}

/**
 * Marca ou desmarca um produto, sem passar do limite.
 *
 * Passar do limite nao "empurra o mais antigo para fora": remover em silencio
 * algo que a pessoa escolheu e pior do que nao aceitar o quarto. O card do
 * quarto fica desabilitado, e o motivo aparece na tela.
 */
export function alternarSelecao(selecao: readonly string[], slug: string): string[] {
  if (selecao.includes(slug)) return selecao.filter((atual) => atual !== slug);
  if (selecao.length >= MAXIMO_PARA_COMPARAR) return [...selecao];
  return [...selecao, slug];
}

/** O endereco da propria lista com a selecao trocada - e o href do card. */
export function comSelecaoAlternada(base: string, filtro: FiltroCatalogo, slug: string): string {
  const copia = clonar(filtro);
  copia.comparar = alternarSelecao(filtro.comparar, slug);
  return comQuery(base, paraQueryDaUrl(copia));
}

/** A mesma lista sem selecao nenhuma. */
export function semSelecao(base: string, filtro: FiltroCatalogo): string {
  const copia = clonar(filtro);
  copia.comparar = [];
  return comQuery(base, paraQueryDaUrl(copia));
}
