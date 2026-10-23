"use server";

import { redirect } from "next/navigation";

import { criarReview, reenviarConfirmacao } from "@/src/lib/api/reviews";
import { ApiError } from "@/src/lib/api/client";
import { ehLocale, type AppLocale } from "@/src/lib/i18n/locales";
import { rotaAvaliar } from "@/src/lib/routes";
import type { EstadoDoFormulario, EstadoDoReenvio } from "@/src/lib/reviews/estado";
import { valoresDoFormData, validar } from "@/src/lib/reviews/validacao";

/**
 * Server Action do formulario (plano DT-10).
 *
 * E o mecanismo do Next que degrada sozinho: sem JavaScript, o navegador faz o
 * POST e o servidor devolve a pagina re-renderizada com este estado. Um route
 * handler exigiria montar redirect e serializar erro na URL a mao.
 *
 * O caminho de sucesso termina em `redirect`, e nao em estado: com POST seguido
 * de redirect, atualizar a pagina depois de enviar nao reenvia a avaliacao - e
 * sem JavaScript essa e a diferenca entre uma tela estavel e um "reenviar
 * formulario?" do navegador.
 */
export async function enviarReview(
  _anterior: EstadoDoFormulario,
  dados: FormData,
): Promise<EstadoDoFormulario> {
  const slug = String(dados.get("slug") ?? "");
  const bruto = String(dados.get("locale") ?? "");
  const locale: AppLocale = ehLocale(bruto) ? bruto : "pt-BR";

  const valores = valoresDoFormData(dados);
  const erros = validar(valores);

  if (Object.keys(erros).length > 0) {
    return { erros, falha: null, valores };
  }

  try {
    await criarReview(slug, locale, {
      rating: Number(valores.rating),
      would_recommend: valores.wouldRecommend === "true",
      title: valores.title.trim(),
      body: valores.body.trim(),
      author_name: valores.authorName.trim(),
      author_email: valores.authorEmail.trim(),
    });
  } catch (erro) {
    if (erro instanceof ApiError && erro.status === 429) {
      return { erros: {}, falha: "limite", valores };
    }
    if (erro instanceof ApiError && erro.status === 400) {
      // A API recusou algo que a validacao daqui deixou passar. O texto da
      // pessoa volta junto - e o mesmo motivo do AC-17.
      return { erros: {}, falha: "invalido", valores };
    }
    return { erros: {}, falha: "indisponivel", valores };
  }

  // Fora do try: `redirect` funciona lancando uma excecao de controle, e
  // captura-la aqui transformaria o sucesso em "indisponivel".
  redirect(`${rotaAvaliar(locale, slug)}?enviado=1`);
}

/**
 * Pede um link de confirmacao novo (AC-04).
 *
 * Nao valida o e-mail em profundidade e nao diz se havia algo para reenviar: a
 * API responde 202 nos dois casos, e uma tela mais informativa que a API viraria
 * exatamente a sonda que o 202 uniforme existe para impedir.
 */
export async function pedirNovoLink(
  _anterior: EstadoDoReenvio,
  dados: FormData,
): Promise<EstadoDoReenvio> {
  const produto = String(dados.get("produto") ?? "");
  const email = String(dados.get("email") ?? "").trim();
  const bruto = String(dados.get("locale") ?? "");
  const locale: AppLocale = ehLocale(bruto) ? bruto : "pt-BR";

  if (!produto || !email) {
    return { enviado: false, falha: true, email };
  }

  try {
    await reenviarConfirmacao(produto, email, locale);
  } catch {
    return { enviado: false, falha: true, email };
  }

  return { enviado: true, falha: false, email };
}
