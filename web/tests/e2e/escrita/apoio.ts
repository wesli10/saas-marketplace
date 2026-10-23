import { randomUUID } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type { APIRequestContext } from "@playwright/test";

/**
 * Apoio dos testes que escrevem (spec 003 secao 8, plano DT-11).
 *
 * Estes testes rodam contra a instancia `e2e-escrita` do compose - banco, API e
 * frontend proprios -, e nunca contra a compartilhada: uma review publicada
 * mudaria a media que outro teste afirma, e os workers rodam em paralelo.
 */

/** A API da instancia de escrita, vista do host. */
export const API = process.env.E2E_API_ESCRITA ?? "http://localhost:8081";

/** A caixa de entrada local. E dela que sai o link de confirmacao. */
export const MAILPIT = process.env.E2E_MAILPIT ?? "http://localhost:8025";

/**
 * A credencial de curadoria, para consultar a fila.
 *
 * Vem da variavel de ambiente quando ela existe e, se nao, do `.env` da raiz -
 * o mesmo arquivo que o compose leu para subir a instancia. Sem isso, quem tem
 * um `.env` proprio veria 401 num teste que nao fala de autenticacao, e
 * perderia a tarde procurando o motivo errado.
 */
function doEnvDaRaiz(chave: string, padrao: string): string {
  if (process.env[chave]) return process.env[chave] as string;

  // Sobe a partir do diretorio de trabalho ate achar o `.env`. Nada de
  // `import.meta.url` aqui: o Playwright transpila estes arquivos para CommonJS,
  // e o modulo nao existe nesse formato.
  let dir = process.cwd();
  for (let nivel = 0; nivel < 4; nivel += 1) {
    const arquivo = join(dir, ".env");
    if (existsSync(arquivo)) {
      const linha = readFileSync(arquivo, "utf8")
        .split("\n")
        .find((l) => l.trimStart().startsWith(`${chave}=`));
      if (linha) return linha.slice(linha.indexOf("=") + 1).trim();
      break;
    }
    dir = dirname(dir);
  }

  // Sem .env: fica o padrao do .env.example, que e o que o CI usa.
  return padrao;
}

/** Cabecalho de HTTP Basic da curadoria, pronto para ir na requisicao. */
export const CABECALHO_DO_CURADOR = {
  Authorization:
    "Basic " +
    Buffer.from(
      `${doEnvDaRaiz("ADMIN_USER", "admin")}:${doEnvDaRaiz("ADMIN_PASSWORD", "troque-esta-senha")}`,
    ).toString("base64"),
};

/**
 * Um e-mail que nenhum outro teste usa (constituicao secao 6).
 *
 * O indice unico da V7 e por (produto, e-mail): reaproveitar um endereco faria
 * o segundo envio responder 202 sem criar nada, e o teste falharia por um
 * motivo que nao tem nada a ver com o que ele afirma.
 */
export function emailUnico(): string {
  return `${randomUUID()}@teste.local`;
}

/**
 * O titulo carrega a marca deste teste - o uuid do e-mail, sem o dominio.
 *
 * E o que permite achar a propria review numa fila compartilhada por workers em
 * paralelo. O local-part e um uuid, entao nao e dado pessoal de ninguem; o
 * e-mail inteiro, esse sim, nunca vai para a tela.
 */
export function tituloDe(email: string): string {
  return `Avaliacao de teste ${email.split("@")[0]}`;
}

/** Corpo valido de review, com o piso de 80 caracteres da spec ja respeitado. */
export function corpoDeReview(email: string, nota = 4) {
  return {
    rating: nota,
    would_recommend: true,
    title: tituloDe(email),
    body:
      "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade. " +
      "A configuracao inicial levou uma tarde.",
    author_name: "Ana Ribeiro",
    author_email: email,
  };
}

/**
 * Espera a mensagem chegar ao Mailpit e devolve o link de confirmacao dela.
 *
 * O envio acontece depois do commit (plano DT-05), entao ele e assincrono em
 * relacao ao 202 que o teste acabou de receber: sem esta espera, o teste leria
 * a caixa antes de a mensagem existir e falharia de vez em quando - o pior tipo
 * de falha, porque ninguem confia mais no gate.
 */
export async function linkDeConfirmacao(
  request: APIRequestContext,
  email: string,
  tentativas = 30,
): Promise<string> {
  for (let i = 1; i <= tentativas; i += 1) {
    const busca = await request.get(`${MAILPIT}/api/v1/search`, {
      params: { query: `to:${email}` },
    });

    if (busca.ok()) {
      const { messages } = (await busca.json()) as { messages: Array<{ ID: string }> };
      const primeira = messages[0];
      if (primeira) {
        const mensagem = await request.get(`${MAILPIT}/api/v1/message/${primeira.ID}`);
        const { Text } = (await mensagem.json()) as { Text: string };

        const link = Text.match(/https?:\/\/\S+\/reviews\/(confirmar|confirm)\?token=\S+/)?.[0];
        if (link) return link;
      }
    }

    await new Promise((ok) => setTimeout(ok, 500));
  }

  throw new Error(`nenhum e-mail para ${email} no Mailpit depois de ${tentativas} tentativas`);
}

/** O token que vai no link, para quem precisa dele solto. */
export function tokenDe(link: string): string {
  return new URL(link).searchParams.get("token") ?? "";
}

/**
 * Cria uma review, confirma o e-mail e faz o curador aprovar - o caminho
 * inteiro, em uma chamada.
 *
 * Existe porque quase todo caso de tela precisa de uma review **publicada**, e
 * repetir os quatro passos em cada arquivo faria quatro versoes ligeiramente
 * diferentes do mesmo fluxo.
 *
 * A review desta chamada e achada na fila pelo **titulo**, que carrega o uuid do
 * e-mail. Pegar "a ultima da fila" seria uma corrida: os workers rodam em
 * paralelo, e a review de outro teste pode entrar entre a confirmacao e a
 * leitura.
 */
export async function reviewPublicada(
  request: APIRequestContext,
  opcoes: { produto: string; idioma?: "pt-BR" | "en"; nota?: number },
): Promise<{ id: string; email: string; titulo: string }> {
  const email = emailUnico();
  const titulo = tituloDe(email);
  const idioma = opcoes.idioma ?? "pt-BR";

  const envio = await request.post(`${API}/api/v1/products/${opcoes.produto}/reviews`, {
    // O idioma da review e o da requisicao (spec secao 4.3), e nao um campo do
    // corpo: e o que impede uma review em portugues marcada como inglesa.
    headers: { "Accept-Language": idioma },
    data: corpoDeReview(email, opcoes.nota ?? 4),
  });
  if (envio.status() !== 202) throw new Error(`envio respondeu ${envio.status()}`);

  const link = await linkDeConfirmacao(request, email);
  await request.post(`${API}/api/v1/reviews/confirm`, { data: { token: tokenDe(link) } });

  const fila = await request.get(
    `${API}/api/v1/admin/reviews?status=PENDING_MODERATION&product=${opcoes.produto}&size=50`,
    { headers: CABECALHO_DO_CURADOR },
  );
  const { items } = (await fila.json()) as { items: Array<{ id: string; title: string }> };

  const minha = items.find((item) => item.title === titulo);
  if (!minha) throw new Error(`a review "${titulo}" nao apareceu na fila`);

  const aprovacao = await request.post(`${API}/api/v1/admin/reviews/${minha.id}/approve`, {
    headers: CABECALHO_DO_CURADOR,
  });
  if (!aprovacao.ok()) throw new Error(`aprovacao respondeu ${aprovacao.status()}`);

  return { id: minha.id, email, titulo };
}
