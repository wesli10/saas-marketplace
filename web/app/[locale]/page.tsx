import { notFound } from "next/navigation";

import { ehLocale } from "@/src/lib/i18n/locales";
import { Home, metadataDaHome } from "@/src/paginas/home";

// A home nao tem segmento traduzido: o idioma sai do proprio [locale], e a
// guarda continua para manter a mesma forma das rotas de par.
export default async function Pagina({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  if (!ehLocale(locale)) notFound();

  return <Home locale={locale} />;
}

// A home serve os dois idiomas na mesma rota, entao o metadata e dinamico: a
// canonica muda com o segmento, o par de hreflang nao.
export async function generateMetadata({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  return ehLocale(locale) ? metadataDaHome(locale) : {};
}
