"use client";

import Link from "next/link";
import { useActionState } from "react";

import { enviarReview } from "@/src/lib/reviews/acoes";
import { ESTADO_INICIAL } from "@/src/lib/reviews/estado";
import { textos } from "@/src/lib/i18n";
import type { AppLocale } from "@/src/lib/i18n/locales";
import { CORPO_MAXIMO, CORPO_MINIMO, type ChaveDeErro } from "@/src/lib/reviews/validacao";
import { rotaPoliticaDeReviews, rotaProduto } from "@/src/lib/routes";
import { TESTID } from "@/src/lib/testid";

/**
 * O primeiro formulario de escrita da aplicacao (plano DT-10).
 *
 * `useActionState` sobre uma Server Action e o mecanismo que degrada sozinho:
 * **com** JavaScript, o envio acontece sem recarregar; **sem** JavaScript, o
 * navegador faz o POST normal e o servidor devolve a pagina re-renderizada com
 * o mesmo estado. E por isso que os campos sao controlados por `defaultValue`,
 * e nao por `useState`: o valor tem de vir do servidor no caminho sem
 * JavaScript, que e onde o `useState` nao existe.
 *
 * O componente e de cliente por causa do hook, e isso e seguro aqui: ele nao
 * importa nada de `server-only` - a action e uma referencia, e nao o modulo da
 * API (AC-24).
 */
export function FormularioDeReview({ slug, locale }: { slug: string; locale: AppLocale }) {
  const t = textos(locale);
  const [estado, acao] = useActionState(enviarReview, ESTADO_INICIAL);

  const erro = (chave?: ChaveDeErro) => (chave ? t.avaliar.erros[chave] : null);
  const temErro = Object.keys(estado.erros).length > 0;

  return (
    <form
      action={acao}
      method="post"
      noValidate
      data-testid={TESTID.avaliarFormulario}
      className="mt-8 space-y-6"
    >
      {/* O slug e o idioma vao no corpo do formulario: sem JavaScript, a action
          nao tem outra forma de saber de qual produto e de qual rota veio. */}
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="locale" value={locale} />

      {temErro ? (
        <p
          role="alert"
          data-testid={TESTID.avaliarResumoDeErros}
          className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-900"
        >
          {t.avaliar.resumoDeErros}
        </p>
      ) : null}

      {estado.falha ? (
        <p
          role="alert"
          data-testid={TESTID.avaliarFalha}
          data-falha={estado.falha}
          className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        >
          {t.avaliar.falhas[estado.falha]}
        </p>
      ) : null}

      <fieldset data-testid={TESTID.avaliarNota}>
        <legend className="font-medium">{t.avaliar.nota}</legend>
        <div className="mt-2 flex flex-wrap gap-4">
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className="flex items-center gap-2 text-sm">
              {/* Id de tipo mais um atributo que diz qual, como manda a
                  convencao de `testid.ts`: um id por opcao seria cinco
                  constantes para a mesma coisa. */}
              <input
                type="radio"
                name="rating"
                value={n}
                data-testid={TESTID.avaliarNotaOpcao}
                data-nota={n}
                defaultChecked={estado.valores.rating === String(n)}
              />
              {t.avaliar.notaOpcao(n)}
            </label>
          ))}
        </div>
        <Erro campo="rating" texto={erro(estado.erros.rating)} />
      </fieldset>

      <fieldset data-testid={TESTID.avaliarRecomendaria}>
        <legend className="font-medium">{t.avaliar.recomendaria}</legend>
        <div className="mt-2 flex gap-4">
          {[
            { valor: "true", rotulo: t.avaliar.sim },
            { valor: "false", rotulo: t.avaliar.nao },
          ].map((opcao) => (
            <label key={opcao.valor} className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="would_recommend"
                value={opcao.valor}
                data-testid={TESTID.avaliarRecomendariaOpcao}
                data-valor={opcao.valor}
                defaultChecked={estado.valores.wouldRecommend === opcao.valor}
              />
              {opcao.rotulo}
            </label>
          ))}
        </div>
        <Erro campo="wouldRecommend" texto={erro(estado.erros.wouldRecommend)} />
      </fieldset>

      <div>
        <label htmlFor="title" className="font-medium">
          {t.avaliar.tituloDaReview}
        </label>
        <input
          id="title"
          name="title"
          type="text"
          maxLength={120}
          defaultValue={estado.valores.title}
          data-testid={TESTID.avaliarTitulo}
          className="mt-2 w-full rounded-md border border-neutral-300 px-3 py-2"
        />
        <Erro campo="title" texto={erro(estado.erros.title)} />
      </div>

      <div>
        <label htmlFor="body" className="font-medium">
          {t.avaliar.corpo}
        </label>
        <p id="body-ajuda" className="mt-1 text-sm text-neutral-500">
          {t.avaliar.corpoAjuda}
        </p>
        <textarea
          id="body"
          name="body"
          rows={8}
          minLength={CORPO_MINIMO}
          maxLength={CORPO_MAXIMO}
          aria-describedby="body-ajuda"
          defaultValue={estado.valores.body}
          data-testid={TESTID.avaliarCorpo}
          className="mt-2 w-full rounded-md border border-neutral-300 px-3 py-2"
        />
        <Erro campo="body" texto={erro(estado.erros.body)} />
      </div>

      <div>
        <label htmlFor="author_name" className="font-medium">
          {t.avaliar.nome}
        </label>
        <input
          id="author_name"
          name="author_name"
          type="text"
          maxLength={80}
          defaultValue={estado.valores.authorName}
          data-testid={TESTID.avaliarNome}
          className="mt-2 w-full rounded-md border border-neutral-300 px-3 py-2"
        />
        <Erro campo="authorName" texto={erro(estado.erros.authorName)} />
      </div>

      <div>
        <label htmlFor="author_email" className="font-medium">
          {t.avaliar.email}
        </label>
        <p id="email-ajuda" className="mt-1 text-sm text-neutral-500">
          {t.avaliar.emailAjuda}
        </p>
        <input
          id="author_email"
          name="author_email"
          type="email"
          maxLength={320}
          aria-describedby="email-ajuda"
          defaultValue={estado.valores.authorEmail}
          data-testid={TESTID.avaliarEmail}
          className="mt-2 w-full rounded-md border border-neutral-300 px-3 py-2"
        />
        <Erro campo="authorEmail" texto={erro(estado.erros.authorEmail)} />
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <button
          type="submit"
          data-testid={TESTID.avaliarEnviar}
          className="rounded-md bg-neutral-900 px-4 py-2 font-medium text-white"
        >
          {t.avaliar.enviar}
        </button>

        <Link href={rotaPoliticaDeReviews(locale)} className="text-sm underline">
          {t.avaliar.politica}
        </Link>

        <Link
          href={rotaProduto(locale, slug)}
          data-testid={TESTID.avaliarVoltar}
          className="text-sm underline"
        >
          {t.avaliar.voltarParaFicha}
        </Link>
      </div>
    </form>
  );
}

function Erro({ campo, texto }: { campo: string; texto: string | null }) {
  if (!texto) return null;

  return (
    <p data-testid={TESTID.avaliarErro} data-campo={campo} className="mt-2 text-sm text-red-700">
      {texto}
    </p>
  );
}
