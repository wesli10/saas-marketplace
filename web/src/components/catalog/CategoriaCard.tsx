import Link from "next/link";

import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { rotaCategoria } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

export interface CategoriaResumo {
  slug: string;
  name: string;
  description?: string | null;
  productCount: number;
}

/**
 * O card carrega `data-slug` alem do `data-testid` de tipo: um id unico por
 * categoria amarraria o teste ao seed do 001, e trocar o seed quebraria a suite
 * (constituicao secao 6).
 */
export function CategoriaCard({
  categoria,
  locale,
}: {
  categoria: CategoriaResumo;
  locale: AppLocale;
}) {
  const t = textos(locale);

  return (
    <li>
      <Link
        href={rotaCategoria(locale, categoria.slug)}
        data-testid={TESTID.categoriaCard}
        data-slug={categoria.slug}
        className="flex h-full flex-col rounded-lg border border-neutral-200 p-4 transition hover:border-neutral-400 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
      >
        <span className="text-lg font-medium">{categoria.name}</span>
        {categoria.description ? (
          <span className="mt-1 text-sm text-neutral-600">{categoria.description}</span>
        ) : null}
        <span className="mt-3 text-sm text-neutral-500" data-testid={TESTID.categoriaContagem}>
          {t.categoria.produtos(categoria.productCount)}
        </span>
      </Link>
    </li>
  );
}
