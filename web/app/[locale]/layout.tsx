import { notFound } from "next/navigation";

import { ehLocale } from "@/src/lib/i18n/locales";
import "../globals.css";

/**
 * Layout raiz da aplicacao: tudo vive sob o segmento de idioma, entao este e o
 * topo da arvore e e ele que carrega <html> e <body>.
 *
 * O segmento e a UNICA fonte do idioma (spec 002 secao 4). Cookie nao muda o
 * conteudo de uma URL ja prefixada - se mudasse, a mesma URL serviria coisas
 * diferentes para pessoas diferentes, e o cache e o buscador veriam uma versao
 * que o visitante nao ve.
 */
export default async function LocaleLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;

  // Idioma desconhecido e 404, e nao um fallback silencioso para pt-BR:
  // /fr/produtos/x nao existe, e responder 200 com conteudo em portugues
  // colocaria uma URL invalida no indice do buscador.
  if (!ehLocale(locale)) {
    notFound();
  }

  return (
    <html lang={locale}>
      <body>{children}</body>
    </html>
  );
}
