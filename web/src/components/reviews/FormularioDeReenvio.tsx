"use client";

import { useActionState } from "react";

import { pedirNovoLink } from "@/src/lib/reviews/acoes";
import { REENVIO_INICIAL } from "@/src/lib/reviews/estado";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

/**
 * O reenvio, na tela de link expirado (spec 003 secao 6).
 *
 * Pede so o e-mail: o produto vem do proprio link do e-mail, num campo oculto.
 * Sem isso, quem clicou tarde demais teria de lembrar e digitar qual produto
 * avaliou - justamente a pessoa que ja perdeu o fio.
 *
 * Server Action pelo mesmo motivo do formulario de avaliacao: funciona sem
 * JavaScript, que e onde esta tela mais provavelmente vai ser aberta - dentro
 * do cliente de e-mail de alguem.
 */
export function FormularioDeReenvio({ produto, locale }: { produto: string; locale: AppLocale }) {
  const t = textos(locale);
  const [estado, acao] = useActionState(pedirNovoLink, REENVIO_INICIAL);

  if (estado.enviado) {
    return (
      <p
        role="status"
        data-testid={TESTID.confirmacaoReenviado}
        className="mt-6 rounded-md border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
      >
        {t.confirmacao.reenviado}
      </p>
    );
  }

  return (
    <form
      action={acao}
      method="post"
      data-testid={TESTID.confirmacaoReenvioFormulario}
      className="mt-6 space-y-3"
    >
      <input type="hidden" name="produto" value={produto} />
      <input type="hidden" name="locale" value={locale} />

      <label htmlFor="email" className="block font-medium">
        {t.confirmacao.reenviarRotulo}
      </label>
      <p id="reenvio-ajuda" className="text-sm text-neutral-500">
        {t.confirmacao.reenviarAjuda}
      </p>

      <input
        id="email"
        name="email"
        type="email"
        maxLength={320}
        required
        aria-describedby="reenvio-ajuda"
        defaultValue={estado.email}
        data-testid={TESTID.confirmacaoReenvioEmail}
        className="w-full rounded-md border border-neutral-300 px-3 py-2"
      />

      {estado.falha ? (
        <p role="alert" className="text-sm text-red-700">
          {t.confirmacao.reenvioIndisponivel}
        </p>
      ) : null}

      <button
        type="submit"
        data-testid={TESTID.confirmacaoReenvioEnviar}
        className="rounded-md bg-neutral-900 px-4 py-2 font-medium text-white"
      >
        {t.confirmacao.reenviar}
      </button>
    </form>
  );
}
