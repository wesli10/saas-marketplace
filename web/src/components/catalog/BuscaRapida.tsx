import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaBusca } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * Formulario GET puro: sem JavaScript o navegador monta `?q=` sozinho e a busca
 * funciona (constituicao secao 6). Nao ha handler nenhum aqui de proposito - o
 * comportamento vem do HTML, e nao de um caminho alternativo mantido em
 * paralelo que so alguem lembra de testar.
 */
export function BuscaRapida({ locale, termo }: { locale: AppLocale; termo?: string }) {
  const t = textos(locale);

  return (
    <form
      action={rotaBusca(locale)}
      method="GET"
      role="search"
      data-testid={TESTID.buscaFormulario}
      className="flex w-full max-w-xl gap-2"
    >
      <label htmlFor="q" className="sr-only">
        {t.busca.rotulo}
      </label>
      <input
        id="q"
        name="q"
        type="search"
        defaultValue={termo}
        placeholder={t.busca.exemplo}
        data-testid={TESTID.buscaCampo}
        className="flex-1 rounded-md border border-neutral-300 px-3 py-2 text-base focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
      />
      <button
        type="submit"
        data-testid={TESTID.buscaEnviar}
        className="rounded-md bg-neutral-900 px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
      >
        {t.busca.enviar}
      </button>
    </form>
  );
}
