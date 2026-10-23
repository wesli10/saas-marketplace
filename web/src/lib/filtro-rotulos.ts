import type { ListaDeIntegracoes } from "@/src/lib/api/catalog";
import type { CampoMultiplo, CampoSimples, FiltroCatalogo } from "@/src/lib/filters";
import type { Messages } from "@/src/lib/i18n";

/**
 * O que esta filtrado, em texto legivel - a lista que o estado vazio mostra
 * junto do botao de remover cada item (spec secao 5.2).
 *
 * Funcao pura, fora do componente, por dois motivos: e regra (qual campo vira
 * rotulo, e como), entao merece teste de unidade; e o rotulo depende do idioma
 * e do catalogo de integracoes, que sao dados, nao markup.
 *
 * `page` e `size` nao entram: sao navegacao, e nao filtro. Remover "pagina 2"
 * como se fosse um criterio nao quer dizer nada.
 */
export interface FiltroAplicado {
  campo: CampoMultiplo | CampoSimples;
  /** Presente so nos multiplos: e o valor que sai, e nao o campo inteiro. */
  valor?: string;
  rotulo: string;
}

export function filtrosAplicados(
  filtro: FiltroCatalogo,
  t: Messages,
  integracoes: ListaDeIntegracoes,
): FiltroAplicado[] {
  const aplicados: FiltroAplicado[] = [];
  const nomeDaIntegracao = new Map(
    (integracoes.items ?? []).map((i) => [i.slug ?? "", i.name ?? i.slug ?? ""]),
  );

  for (const valor of filtro.integration) {
    aplicados.push({
      campo: "integration",
      valor,
      // Slug cru como ultimo recurso: integracao que sumiu do catalogo ainda
      // precisa poder ser removida do filtro.
      rotulo: nomeDaIntegracao.get(valor) ?? valor,
    });
  }

  for (const valor of filtro.pricing_model) {
    const conhecido = valor in t.filtro.cobrancaRotulo;
    aplicados.push({
      campo: "pricing_model",
      valor,
      rotulo: conhecido
        ? t.filtro.cobrancaRotulo[valor as keyof Messages["filtro"]["cobrancaRotulo"]]
        : valor,
    });
  }

  if (filtro.q) aplicados.push({ campo: "q", rotulo: `“${filtro.q}”` });
  if (filtro.has_free_trial)
    aplicados.push({ campo: "has_free_trial", rotulo: t.filtro.testeGratis });
  if (filtro.has_free_plan)
    aplicados.push({ campo: "has_free_plan", rotulo: t.filtro.planoGratuito });
  if (filtro.price_max) {
    aplicados.push({ campo: "price_max", rotulo: `${t.filtro.precoAte} ${filtro.price_max}` });
  }

  return aplicados;
}
