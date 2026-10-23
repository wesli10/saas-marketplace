import type { AppLocale } from "./i18n/locales";

export type Periodo = "MONTH" | "YEAR" | "ONE_TIME";

/**
 * Formatacao de preco com `Intl`, e nao com string montada a mao.
 *
 * O catalogo tem preco em BRL e em USD, e o simbolo, a posicao dele e o
 * separador decimal mudam com o par (moeda, idioma): R$ 135,00 em pt-BR e
 * US$ 19.99 em en. Escrever isso a mao daria certo nos casos que alguem testou.
 */
export function precoFormatado(
  locale: AppLocale,
  valor: number | null | undefined,
  moeda: string | null | undefined,
): string | null {
  if (valor === null || valor === undefined || !moeda) return null;

  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: moeda,
    // Preco de tabela nao precisa de centavos quando eles sao zero, e "R$ 135"
    // le melhor que "R$ 135,00" numa lista de cards.
    minimumFractionDigits: Number.isInteger(valor) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(valor);
}

/** A inicial do nome, para o lugar do logo que o catalogo ainda nao tem. */
export function inicialDe(nome: string): string {
  return (nome.trim()[0] ?? "?").toUpperCase();
}

export function dataFormatada(locale: AppLocale, iso: string | null | undefined): string | null {
  if (!iso) return null;
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) return null;

  return new Intl.DateTimeFormat(locale, { dateStyle: "long", timeZone: "UTC" }).format(data);
}

/**
 * Nome do idioma e do pais no idioma de quem le, via `Intl.DisplayNames`.
 *
 * A API devolve codigo (`en`, `US`), que e o certo para maquina e ilegivel na
 * tela. Uma tabela nossa de codigo para nome seria dado de produto escrito de
 * cabeca (constituicao secao 7) e ficaria desatualizada; o `Intl` ja tem a
 * tabela, traduzida.
 *
 * Codigo desconhecido volta como veio: melhor mostrar `ZZ` do que esconder que
 * o dado existe.
 */
export function nomeDoIdioma(locale: AppLocale, codigo: string): string {
  return nomeDe(locale, "language", codigo);
}

export function nomeDoPais(locale: AppLocale, codigo: string): string {
  return nomeDe(locale, "region", codigo);
}

function nomeDe(locale: AppLocale, tipo: "language" | "region", codigo: string): string {
  try {
    return new Intl.DisplayNames([locale], { type: tipo }).of(codigo) ?? codigo;
  } catch {
    return codigo;
  }
}
