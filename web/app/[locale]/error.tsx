"use client";

import { usePathname } from "next/navigation";

import { textos } from "@/src/lib/i18n";
import { ehLocale, LOCALE_PADRAO } from "@/src/lib/i18n/locales";
import { TESTID } from "@/src/lib/testid";

/**
 * Boundary de erro. O texto vem do catalogo do frontend, nunca do erro: em
 * producao o Next entrega so um digest, e mesmo que entregasse a mensagem, o
 * `detail` da API vem no idioma da requisicao e nao substitui uma explicacao
 * escrita para quem esta lendo a pagina (AC-19).
 */
export default function Erro({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const caminho = usePathname() ?? "";
  const primeiro = caminho.split("/")[1] ?? "";
  const locale = ehLocale(primeiro) ? primeiro : LOCALE_PADRAO;
  const t = textos(locale);

  return (
    <main className="p-8" data-testid={TESTID.paginaDeErro}>
      <h1 className="text-2xl font-semibold">{t.erro.falhaTitulo}</h1>
      <p className="mt-2">{t.erro.falhaTexto}</p>
      <button className="mt-4 underline" onClick={reset} data-testid={TESTID.tentarDeNovo}>
        {t.erro.tentarDeNovo}
      </button>
    </main>
  );
}
