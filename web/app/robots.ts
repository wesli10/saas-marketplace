import type { MetadataRoute } from "next";

import { urlAbsoluta } from "@/src/lib/seo/urls";

/**
 * Libera tudo e aponta o sitemap.
 *
 * A comparacao **nao** entra em `disallow`, embora seja `noindex`: bloquear o
 * rastreamento impediria o robo de ler justamente a diretiva que pede para nao
 * indexar. Pagina que se quer fora do indice precisa ser visitada para dizer
 * isso.
 */
export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/" }],
    sitemap: urlAbsoluta("/sitemap.xml"),
    host: urlAbsoluta("/"),
  };
}
