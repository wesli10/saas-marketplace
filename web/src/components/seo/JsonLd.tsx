import type { JsonLd as Dados } from "@/src/lib/seo/jsonld";

/**
 * Serializa o bloco de dados estruturados.
 *
 * `dangerouslySetInnerHTML` e o unico jeito de emitir JSON dentro de um
 * `<script>` no React, e por isso o `<` vai escapado: sem isso, um `</script>`
 * dentro de qualquer texto do catalogo fecharia a tag e o resto viraria HTML.
 * O conteudo aqui vem da API, entao e entrada, e nao constante.
 */
export function JsonLd({ dados }: { dados: Dados | Dados[] }) {
  const texto = JSON.stringify(dados).replace(/</g, "\\u003c");

  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: texto }} />;
}
