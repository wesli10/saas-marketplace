"use client";

import { usePathname } from "next/navigation";

import { textos } from "@/src/lib/i18n";
import { ehLocale, LOCALE_PADRAO } from "@/src/lib/i18n/locales";
import { rotaHome } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * O `not-found.tsx` do Next nao recebe os params da rota, entao o idioma sai do
 * proprio caminho. Fazer isso por cabecalho exigiria middleware em toda
 * requisicao - caro para uma pagina que quase ninguem ve (plano DT-04).
 */
export default function NaoEncontrado() {
  const caminho = usePathname() ?? "";
  const primeiro = caminho.split("/")[1] ?? "";
  const locale = ehLocale(primeiro) ? primeiro : LOCALE_PADRAO;
  const t = textos(locale);

  return (
    <main className="p-8" data-testid={TESTID.paginaNaoEncontrada}>
      <h1 className="text-2xl font-semibold">{t.erro.naoEncontradoTitulo}</h1>
      <p className="mt-2">{t.erro.naoEncontradoTexto}</p>
      <a
        className="mt-4 inline-block underline"
        href={rotaHome(locale)}
        data-testid={TESTID.linkHome}
      >
        {t.navegacao.irParaHome}
      </a>
    </main>
  );
}
