import { FiltroAutoSubmit } from "@/src/components/catalog/FiltroAutoSubmit";
import type { ListaDeIntegracoes } from "@/src/lib/api/catalog";
import type { FiltroCatalogo } from "@/src/lib/filters";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

const MODELOS = [
  "FREE",
  "FREEMIUM",
  "SUBSCRIPTION",
  "USAGE_BASED",
  "ONE_TIME",
  "CONTACT_SALES",
] as const;

/**
 * Formulario GET, sem handler nenhum.
 *
 * Cada `name` e exatamente o parametro da API, entao sem JavaScript o navegador
 * monta a query certa sozinho: o AC-09 sai do mecanismo, e nao de um caminho
 * alternativo que so alguem lembra de testar (plano DT-05).
 *
 * O `action` aponta para a propria pagina, para o filtro nao tirar o visitante
 * da categoria em que ele esta.
 */
export function FiltroPainel({
  locale,
  acao,
  filtro,
  integracoes,
}: {
  locale: AppLocale;
  acao: string;
  filtro: FiltroCatalogo;
  integracoes: ListaDeIntegracoes;
}) {
  const t = textos(locale);
  const idDoFormulario = "filtro-catalogo";

  // Integracao sem produto e um filtro que so pode produzir lista vazia.
  const disponiveis = (integracoes.items ?? []).filter((i) => (i.product_count ?? 0) > 0);

  return (
    <form
      id={idDoFormulario}
      method="GET"
      action={acao}
      data-testid={TESTID.filtroPainel}
      className="flex flex-col gap-6"
    >
      <FiltroAutoSubmit formulario={idDoFormulario} />
      <h2 className="text-sm font-medium tracking-wide text-neutral-500 uppercase">
        {t.filtro.titulo}
      </h2>

      {/* `q` sobrevive ao filtro: sem isto, filtrar dentro de uma busca apagaria a busca. */}
      {filtro.q ? <input type="hidden" name="q" value={filtro.q} /> : null}

      {/* E a selecao de comparacao tambem: marcar tres produtos e depois
          filtrar nao pode apagar a selecao sem avisar (T24). */}
      {filtro.comparar.map((slug) => (
        <input key={slug} type="hidden" name="comparar" value={slug} />
      ))}

      {disponiveis.length > 0 && (
        <fieldset>
          <legend className="mb-2 font-medium">{t.filtro.integracoes}</legend>
          <div className="flex flex-col gap-1.5">
            {disponiveis.map((integracao) => (
              <label key={integracao.slug} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  name="integration"
                  value={integracao.slug}
                  defaultChecked={filtro.integration.includes(integracao.slug ?? "")}
                  data-testid={TESTID.filtroIntegracao}
                  data-slug={integracao.slug}
                />
                {integracao.name}
              </label>
            ))}
          </div>
        </fieldset>
      )}

      <fieldset>
        <legend className="mb-2 font-medium">{t.filtro.cobranca}</legend>
        <div className="flex flex-col gap-1.5">
          {MODELOS.map((modelo) => (
            <label key={modelo} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                name="pricing_model"
                value={modelo}
                defaultChecked={filtro.pricing_model.includes(modelo)}
                data-testid={TESTID.filtroCobranca}
                data-valor={modelo}
              />
              {t.filtro.cobrancaRotulo[modelo]}
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend className="mb-2 font-medium">{t.filtro.gratuidade}</legend>
        <div className="flex flex-col gap-1.5">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              name="has_free_trial"
              value="true"
              defaultChecked={filtro.has_free_trial === "true"}
              data-testid={TESTID.filtroTesteGratis}
            />
            {t.filtro.testeGratis}
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              name="has_free_plan"
              value="true"
              defaultChecked={filtro.has_free_plan === "true"}
              data-testid={TESTID.filtroPlanoGratuito}
            />
            {t.filtro.planoGratuito}
          </label>
        </div>
      </fieldset>

      {/* Campo unico usa `label`, e nao `fieldset`/`legend`: a legenda nomeia o
          GRUPO, e um grupo de um so deixa o campo sem nome acessivel. Quem
          navega por leitor de tela ouvia "editar texto" e mais nada - achado
          pelo axe no T27. */}
      <div>
        <label htmlFor="price_max" className="mb-2 block font-medium">
          {t.filtro.precoAte}
        </label>
        <input
          id="price_max"
          type="number"
          inputMode="decimal"
          min="0"
          step="1"
          name="price_max"
          defaultValue={filtro.price_max ?? ""}
          data-testid={TESTID.filtroPrecoMax}
          className="w-32 rounded-md border border-neutral-300 px-2 py-1 text-sm"
        />
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="submit"
          data-oculto-com-js
          data-testid={TESTID.filtroAplicar}
          className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white"
        >
          {t.filtro.aplicar}
        </button>
        <a href={acao} data-testid={TESTID.filtroLimpar} className="self-center text-sm underline">
          {t.filtro.limparTudo}
        </a>
      </div>
    </form>
  );
}
