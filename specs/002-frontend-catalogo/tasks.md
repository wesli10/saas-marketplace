# Tasks 002 — Frontend do catálogo

| Campo | Valor |
|---|---|
| Spec | `specs/002-frontend-catalogo/spec.md` |
| Plano | `specs/002-frontend-catalogo/plan.md` |
| Constituição | `CLAUDE.md` (raiz) |
| Total | 29 tarefas (T00–T28) — **26 concluídas, 3 pendentes (T25–T27)** |
| Data | 2026-08-26 |

## Como usar

- **Uma tarefa por commit.** Mensagem começa com `[002-TNN]`.
- Uma tarefa só está pronta quando o gate dela passa (`npm run verify` em `web/`, ou `./mvnw verify` quando toca Java) **e** o critério de verificação foi observado.
- **Confira o gate pelo código de saída, nunca filtrando a saída com `grep`.** Em 2026-08-27 o T17 entrou com duas falhas — uma varredura e um teste de unidade — porque o filtro que eu usava para resumir a saída escondia exatamente as linhas de erro. `npm run verify; echo $?` é a forma correta.
- **Toda tarefa de tela entrega três coisas no mesmo commit:** a tela, os `data-testid` dela (constantes em `lib/testid.ts`) e o teste e2e do AC correspondente. Tela sem teste não fecha tarefa (spec §7.1).
- **A smoke não cresce por conveniência.** São cinco casos, orçamento de 5 min. Marcar um sexto com `@smoke` exige justificativa no corpo do commit.
- **Todo teste é independente** (spec §7.2): ordem arbitrária, worker arbitrário, nenhum dado compartilhado. A suíte e2e **não escreve** — nada de `/admin/**`, nada de requisição que não seja `GET`. Cenário que precise de outro backend ganha outra instância no compose, nunca uma mutação no ambiente compartilhado.
- Se a implementação mostrar que a spec está errada, **para**, atualiza `spec.md`, depois continua (constituição §2.5).
- Dependência nova exige uma linha na tabela do `plan.md` §DT-02 no mesmo commit.

---

## Fase 0 — Constituição e fundação

### T00 — Abrir a constituição para o frontend ✅
**Status:** concluída em 2026-08-27 — `CLAUDE.md` §3 (stack do frontend e dos testes), §4 (bloco de arquitetura do `web/`), §6 (regras de e2e, `data-testid`, independência e as duas suítes) e §8 (item de DoD do frontend). `grep "fase posterior"` só retorna a linha de IA, que segue correta.
**Objetivo:** hoje `CLAUDE.md` §3 diz que o frontend "não faz parte das specs iniciais" e §4 descreve só o monolito Java. Enquanto isso for verdade no papel, toda tarefa desta fatia é uma violação.
**Entrega:** §3 com a linha de frontend preenchida (Next.js, TS, Tailwind, shadcn/Radix, Vitest, Playwright); §4 com um parágrafo de arquitetura do `web/` (SSR, estado na URL, sem banco, sem BFF); §6 com a regra de teste — **e2e desde a primeira tarefa, `data-testid` obrigatório em elemento alcançado por teste, smoke no CI e full sob demanda, e todo teste independente numa suíte que roda em paralelo e não escreve**; §8 com o DoD do frontend.
**Verificação:** `grep -n "fase posterior" CLAUDE.md` não retorna a linha de frontend. `grep -n "data-testid" CLAUDE.md` retorna a regra, e `grep -n "paralelo" CLAUDE.md` retorna a de independência.
**ACs:** — (processo)

### T01 — Esqueleto do `web/` que builda ✅
**Status:** concluída em 2026-08-27 — `next@16.3.3`, `react@19.2.8`, `typescript@5`, `tailwindcss@4`, `eslint@9`. `npm run verify` (formato + lint + tipos + Vitest) verde: **1 arquivo de teste, 1 teste**. `npm run build` compila em 2,3 s. Plano §DT-01 e §DT-02 atualizados no mesmo commit.
**Objetivo:** um Next.js mínimo, TS estrito, Tailwind, ESLint e Prettier — nada de página real ainda.
**Entrega:** `web/` com `package.json` (scripts `dev`, `build`, `lint`, `typecheck`, `test`, `verify`), `tsconfig.json` com `strict: true`, Tailwind configurado, uma página placeholder.
**Verificação:** `cd web && npm ci && npm run verify` verde num clone limpo.
**ACs:** —
**Depende de:** T00

### T02 — `web` no compose, subindo com a API ✅
**Status:** concluída em 2026-08-27 — `docker compose down -v && docker compose up --build -d --wait` sobe os três serviços e os três ficam `Healthy`. `curl localhost:3000` → **200**, com o HTML já trazendo `data-testid="placeholder-titulo"` (é SSR, não hidratação). `docker compose exec web id -u` → **1001** (usuário `app`). Imagem do frontend: **325 MB**.
**Objetivo:** fechar AC-01 antes de existir conteúdo, porque é o gate que a constituição §8 cobra — e porque o e2e da tarefa seguinte precisa dele de pé.
**Entrega:** `web/Dockerfile` multi-stage com `output: standalone` e usuário não-root; serviço `web` no `docker-compose.yml` da raiz, `depends_on: app`, `API_BASE_URL` interno, `WEB_PORT` (default 3000) em `.env.example`.
**Verificação:** `docker compose down -v && docker compose up --build -d --wait` → `curl -s -o /dev/null -w '%{http_code}' localhost:3000` → `200`. `docker compose exec web id -u` → não-root.
**ACs:** AC-01
**Depende de:** T01

### T03 — Playwright contra o compose, com o primeiro smoke e a convenção de `data-testid` ✅
**Status:** concluída em 2026-08-27 — `@playwright/test@1.62.1`, cinco projetos configurados, `fullyParallel`. `npm run e2e:smoke` verde contra o compose (**1 teste, 199 ms**); `npm run e2e:isolamento` verde na topologia invertida (**2 execuções**). As duas varreduras foram exercitadas em **7 gatilhos**, todos saindo com código 1: botão sem `data-testid`, id vindo do seed, `let` de módulo, `describe.serial`, `request.post`, `getByText` de navegação e rota `/admin/`. **Desvio:** o Playwright não tem `--shuffle` — spec, plano e tarefas corrigidos no mesmo commit (plano §DT-10.3, risco 9).
**Objetivo:** a exigência da spec §7.1 — e2e existe antes da primeira tela, não depois da última.
**Entrega:** `playwright.config.ts` com `fullyParallel: true`, `workers: '50%'` (2 no CI), os cinco projetos (`chromium`, `webkit`, `chromium-no-js`, `mobile-chromium`, `resiliencia`) e `globalTimeout` de 5 min no recorte de smoke; scripts `e2e`, `e2e:smoke` (`--grep @smoke`) e `e2e:isolamento` (`--workers=1 --repeat-each=2`); `lib/testid.ts` com as constantes; o primeiro teste `@smoke` (a página placeholder responde 200 com HTML); `scripts/check-testids.mjs` e `scripts/check-e2e-isolation.mjs` (proíbe `describe.serial`, estado de módulo, `beforeAll` com rede, método não-`GET`, `/admin/`, e `getByText` fora de asserção).
**Verificação:** `npm run e2e:smoke` verde com o compose de pé, e `npm run e2e:isolamento` verde na topologia invertida. Cada gatilho das varreduras testado uma vez: `<button>` sem `data-testid`, `describe.serial`, `request.post`, `getByText` de navegação — os quatro fazem o script falhar.
**ACs:** AC-26, AC-27, AC-30, AC-31, AC-32
**Depende de:** T02

### T04 — Reestruturar o CI: filtro por path, imagem em paralelo, smoke em todo push ✅
**Status:** concluída em 2026-08-27 — quatro jobs (`o que mudou`, `backend`, `imagem`, `web`), com `imagem` sem `needs`, em paralelo ao `backend`.

Observado em pushes reais:
- push só de `.github/` → **os quatro jobs verdes, 3–6 s cada**, reportando sem trabalhar;
- push só de `web/` → `backend` e `imagem` **verdes em 5 s**, `web` rodou inteiro em **196 s** (npm ci 17 s, verify 9 s, build 16 s, compose 111 s, chromium 30 s, smoke 2 s). Dentro dos 5 min do AC-28, com ~1 min 44 s de folga.
- O caso "push só de backend não dispara o `web`" foi exercitado no classificador contra quatro commits reais do histórico (incluindo um só de Java), mas **ainda não por um push de verdade**. Já escorregou três vezes, e cada vez por um motivo diferente:
  1. o T07 tocou `web/` junto (os tipos gerados), então não serviu;
  2. o T28 nasceu numa branch nova — e **branch nova não tem base comparável**, então o classificador cai no "roda tudo" de propósito;
  3. no merge do T28 para a `dev` a base existia, mas o PR levava junto um commit de e2e — o log do run `33075293349` mostra `web/tests/e2e/paginacao.spec.ts` na lista e `-> backend=true web=true`. O classificador acertou; **quem errou foi o sequenciamento**, meu.

  **A condição que fecha:** um push só de `src/`, `pom.xml` ou `specs/` numa branch que já existe. Não vale inventar um commit para isso — o valor da verificação está em ela acontecer no fluxo real. Enquanto não acontecer, o que se sabe é: o caminho simétrico (push só de `web/` → `backend` reporta em ~26 s sem rodar `mvnw`) foi observado várias vezes, e é o **mesmo mecanismo** (`if: env.RODA == 'true'` em cada passo), só que com a outra saída do classificador.

**Nada pendente do lado da proteção de branch** — e a versão anterior desta linha estava errada. Eu registrei aqui, duas vezes, que os required checks da `dev` precisavam ser atualizados para os nomes novos. Não precisavam: **não há proteção de branch neste repositório**, porque ele é privado num plano Free, onde o recurso não existe (verificado em 2026-08-27 — a API responde 403). O merge nunca foi gateado, nem antes nem depois desta tarefa. Decisão de Wesley em 2026-08-27: manter assim; o gate segue sendo convenção (constituição §2).

**Achado do primeiro run:** `tsc --noEmit` sozinho passa localmente e falha em checkout limpo, porque `LayoutProps<"/">` é gerado pelo Next em `.next/types/`. O `typecheck` virou `next typegen && tsc --noEmit` (plano §DT-01).
**Objetivo:** `mvn verify` parar de rodar quando só o `web/` muda, sem deixar check pendente (plano §DT-12, risco 4); a smoke virar gate de push; e a imagem sair do job de teste.
**Por que tudo junto:** mexer no conjunto de jobs em duas etapas dobra o retrabalho e deixa a coluna de status confusa no meio do caminho. É uma reestruturação só. (A justificativa original falava em atualizar required checks duas vezes — não se aplica: não há proteção de branch aqui.)
**Entrega:** `ci.yml` com três jobs — `backend` (`paths` ignorando `web/**`; só `mvnw verify` e os relatórios), `imagem` (Buildx + `docker/build-push-action`, **sem `needs`**, paralelo ao `backend`) e `web` (`paths` ignorando `src/**` e `pom.xml`: lint + typecheck + Vitest + build + `docker compose up -d --wait` + `e2e:smoke`); relatório do Playwright como artefato só em falha; o par `if: always()` que reporta sucesso no job pulado.
**Verificação:** push tocando só `web/README.md` não dispara `backend`; push tocando só `pom.xml` não dispara `web`; nenhum check fica pendente nos dois casos. `backend` e `imagem` aparecem começando no mesmo instante no run. O job `web` fecha em ≤ 5 min.
**ACs:** AC-02, AC-28
**Depende de:** T03

### T05 — Workflow da suíte full, na promoção para `homol` ✅
**Status:** **verificada em 2026-08-27** — a promoção da `dev` para a `homol` (PR #19) rodou a suíte completa verde: **232 testes em 2,4 min** com 2 workers, mais **146 execuções da topologia invertida** em 54 s. Job inteiro em 9 min 9 s, dos quais ~5 min são o `docker compose --build`. Run `33080893190`.

**Foram precisas duas execuções, e a primeira é a que valeu.** Implementada em 2026-08-27 e deixada pendente de propósito: o critério era ver a execução verde, não a existência do arquivo.

**A primeira execução aconteceu ao criar a `homol`** (run `33078844437`), e encontrou o defeito que só ela poderia encontrar: o passo `sobe o compose` fazia `docker compose up` **sem `--profile e2e`**, então o serviço `web-sem-api` não subia e os três testes do projeto `resiliencia` falhavam com `ERR_CONNECTION_REFUSED` na 3001. Os outros 165 passaram.

**Por que só aqui:** o CI de push roda a *smoke*, que não inclui o projeto `resiliencia`; o perfil `e2e` nasceu no T14, depois deste workflow, e o `ci.yml` foi atualizado na época — este não, porque nunca tinha sido executado. É a definição de workflow escrito e não exercitado: ele apodrece em silêncio, e a diferença entre os dois arquivos passou cinco tarefas sem ninguém ver.

**Corrigido** no mesmo dia (`--profile e2e` no compose e no passo de logs), e a segunda promoção passou de ponta a ponta.

**O passo `topologia invertida` nunca tinha rodado no CI** — na execução que falhou ele ficou `skipped`, porque a suíte morreu antes. Rodou agora, e passou. Era a segunda surpresa possível deste workflow, e não veio.

**A lição que fica registrada:** workflow escrito e não exercitado apodrece em silêncio. Este ficou cinco tarefas fora de sincronia com o `ci.yml` sem ninguém ver, e nenhuma revisão de código pegaria — só a execução.

**Mudança de desenho (2026-08-27, proposta do Wesley):** o gatilho era só `workflow_dispatch`, e isso não funcionaria — o GitHub só exibe o disparo manual quando o workflow está no **branch padrão**, que aqui é a `main`, e `main` só recebe release. A suíte ficaria escrita e nunca exercitada até o primeiro release.

Push na `homol` não tem essa restrição, e o desenho fica melhor do que era: promover **é** o gatilho, então "rodar a full antes da `main`" deixa de ser uma linha de README que alguém precisa lembrar e vira topologia. `main` só recebe o que passou pela `homol`, e como o check fica ancorado no SHA, ele já aparece verde no PR de release quando a promoção é fast-forward. O fluxo entrou na constituição §2.
**Objetivo:** o outro gatilho da exigência: a full não roda a cada push, mas precisa existir e ser executável com um clique.
**Entrega:** `.github/workflows/e2e-full.yml` com `push` na `homol` mais `workflow_dispatch`, os cinco projetos do Playwright, axe incluído (quando o T27 o trouxer), sem `globalTimeout`; nota no `README.md` de que rodar a full é passo obrigatório antes de promover para `main`.
**Verificação:** promover para `homol` e ver a execução verde. A full inclui todo teste marcado `@smoke`.
**Correção (2026-08-27):** a entrega dizia "entrada de branch". Não há: o `workflow_dispatch` já traz o seletor de ref na UI, e um input separado só criaria o caso de rodar o código de uma branch achando que rodou o de outra. O input que existe é `isolamento`, para rodar também a topologia invertida no mesmo disparo.
**ACs:** AC-29
**Depende de:** T04

### T06 — Tipos gerados a partir do OpenAPI ✅
**Status:** concluída em 2026-08-27 — `openapi-typescript@7.13.0` gera **1.887 linhas** em `src/lib/api/types.gen.ts` a partir de `/v3/api-docs`. Geração determinística (duas execuções seguidas dão arquivos idênticos), com carimbo de "não editar" acrescentado depois, sempre igual — carimbo variável faria o CI acusar mudança a cada run. Passo `tipos em dia com o OpenAPI` no job `web`, depois do compose subir. **Detecção verificada com o arquivo rastreado:** edição à mão faz o `git diff` acusar; regenerar limpa. (Na primeira tentativa a prova passou batido porque o arquivo ainda era novo — `git diff` não enxerga não rastreado.)
**Objetivo:** o contrato do 001 vira tipo, e divergência quebra o build.
**Entrega:** script `api:types` rodando `openapi-typescript` contra `/v3/api-docs`; `types.gen.ts` commitado com cabeçalho de "não editar"; step no CI que regenera e falha se houver diff.
**Verificação:** editar `types.gen.ts` à mão e rodar o step → falha. Regenerar → sem diff.
**ACs:** AC-23
**Depende de:** T02

---

## Fase 1 — Backend: o desvio aprovado

### T07 — Campo `alternates` na categoria (desvio da spec §4.1) ✅
**Status:** concluída em 2026-08-27 — `CategoryDetailView` novo (a árvore não podia ganhar campo nulo), projetado de `slug_pt`/`slug_en`. `./mvnw verify` verde: **220 testes**, dos quais 3 novos em `CategoriaAlternatesIT` (ficha, entrada pelo slug em inglês, e a árvore seguindo sem o campo). `specs/001-catalogo-core/spec.md` emendada no mesmo commit (§10.1). Nenhum arquivo novo em `db/migration`.
**Objetivo:** sem o slug do outro idioma não há seletor de idioma nem hreflang na página de categoria.
**Escopo corrigido em 2026-08-27:** era "nas três rotas". Produto tem **um** slug (spec 001 §4.2 — nome de produto não se traduz), então lá o campo seria a mesma string duas vezes. Sobra a categoria.
**Entrega:** `alternates` em `GET /categories/{slug}`, projetado de `slug_pt`/`slug_en`, num DTO próprio (o da árvore não pode ganhar campo nulo — `default-property-inclusion: always`); OpenAPI atualizado; **`specs/001-catalogo-core/spec.md` atualizada no mesmo commit**.
**Verificação:** teste de integração afirma os dois slugs na rota, nos dois idiomas de entrada, e que `GET /categories` **não** traz o campo. `git show --stat` do commit inclui `specs/001-catalogo-core/spec.md`. Nenhum arquivo novo em `src/main/resources/db/migration`.
**ACs:** AC-25
**Depende de:** —

### T08 — CORS restrito por ambiente ✅
**Status:** concluída em 2026-08-27 — `CORS_ALLOWED_ORIGINS` vazio por padrão devolve `null` como política, e o preflight é recusado. `./mvnw verify` verde: **223 testes**, 3 novos (`CorsDesligadoIT`, `CorsLigadoIT`), cobrindo os três casos: sem variável recusa, origem listada passa, outra origem continua recusada. Só `GET`/`HEAD`/`OPTIONS` liberados — curadoria não acontece por navegador nesta fatia.
**Objetivo:** `next dev` fora do compose precisa da origem liberada; produção não precisa de nenhuma (plano §DT-11).
**Entrega:** `CORS_ALLOWED_ORIGINS` (vazio por padrão) na configuração de segurança.
**Verificação:** com a variável vazia, preflight de `localhost:3000` é negado; com a origem listada, é aceito. Teste de integração para os dois casos.
**ACs:** —
**Depende de:** —

---

## Fase 2 — Fundações do frontend

### T09 — Idioma na borda: segmento `[locale]`, 404 para o resto ✅
**Status:** concluída em 2026-08-27 — tudo passou para `app/[locale]/`, que virou o layout raiz (é ele que carrega `<html lang>` e `<body>`; o `app/layout.tsx` deixou de existir, porque com todas as rotas sob o segmento o topo da árvore é outro). e2e verde: `/pt-BR` → 200 com `lang="pt-BR"`, `/en` → 200 com `lang="en"`, `/fr` → **404**.
**Entrega:** `AppLocale`, `[locale]/layout.tsx` validando o segmento e chamando `notFound()`, `<html lang>` correto.
**Verificação:** e2e: `/pt-BR` e `/en` respondem 200 com o `lang` certo; `/fr` responde 404.
**ACs:** AC-04 (parcial)
**Depende de:** T01

### T10 — Redirect da raiz por `Accept-Language` ✅
**Status:** concluída em 2026-08-27 — `middleware.ts` com `matcher: "/"` e nada mais. Negociação em função pura (`idiomaPreferido`), **7 testes de unidade**: subtag primária (`pt-PT` → `pt-BR`), ordenação por `q` e não por posição, `q=0` como recusa, curinga e idioma desconhecido caindo no padrão. e2e confere o **307** (não o 308) e o destino, nos três casos.
**Entrega:** `middleware.ts` fazendo só isso.
**Verificação:** e2e: requisição a `/` com `Accept-Language: en` chega em `/en`; sem header, em `/pt-BR`; `fr-FR` cai em `/pt-BR`. Confere o 307, não só o destino.
**ACs:** AC-03
**Depende de:** T09

### T11 — Mapa de rotas traduzidas ✅
**Status:** concluída em 2026-08-27 — `lib/routes.ts` com os cinco pares, **5 testes de unidade** cobrindo os dois idiomas de cada um. As dez rotas do `app/` são arquivos de três linhas que amarram o idioma e delegam a uma implementação só.

**Achado não previsto pela spec:** como os dois segmentos do par vivem sob `[locale]`, `/pt-BR/categories/x` casaria com a rota inglesa e renderizaria igual — duas URLs para a mesma página, que é justamente o que o prefixo obrigatório existe para evitar. Cada rota agora recebe o idioma que ela serve e responde **404** na combinação cruzada. 8 testes e2e: os quatro pares respondendo e as quatro combinações cruzadas dando 404.
**Entrega:** `lib/routes.ts` com `(pagina, locale, params) → caminho`, cobrindo os cinco pares; as rotas do `app/` como reexports.
**Verificação:** unidade cobre os dois idiomas de cada página. Nenhum link montado por template string (`grep` por `` `/${locale}/ `` volta vazio).
**ACs:** AC-04
**Depende de:** T09

### T12 — Cliente da API, server-only ✅
**Status:** concluída em 2026-08-27 — `client.ts` (timeout por `AbortController`, `Accept-Language`, `ApiError` com Problem Details já lido) e `catalog.ts` com as seis funções de caso de uso, tipadas a partir de `types.gen.ts`. **7 testes de unidade**: 200, lista virando parâmetro repetido, 404 com `missing_slugs`, 400 com `errors[]`, 5xx sem corpo JSON, timeout e API fora do ar. **Guarda verificada:** um componente `"use client"` importando `lib/api/client` faz o build falhar com *"You're importing a module that depends on server-only"*.
**Entrega:** `lib/api/client.ts` (timeout, `Accept-Language`, `ApiError` com Problem Details parseado) e `lib/api/catalog.ts`; pacote `server-only` no módulo.
**Verificação:** importar `lib/api` de um componente `"use client"` quebra o build. Unidade cobre 200, 400, 404, 5xx e timeout.
**ACs:** AC-24 (parcial)
**Depende de:** T06

### T13 — Catálogo de textos com paridade garantida por tipo ✅
**Status:** concluída em 2026-08-27 — `messages.pt-BR.ts` é a fonte, `messages.en.ts` deriva o tipo dela. Guarda verificada: remover `tentarDeNovo` do inglês produz `TS2741: Property 'tentarDeNovo' is missing`.

**Armadilha encontrada:** `type Messages = typeof ptBR` (a versão que a tarefa descrevia) **não funciona** — com `as const`, cada texto vira tipo literal, e o `tsc` passa a reclamar de *texto traduzido* em vez de chave faltando. O contrato precisa de um mapa que troque os literais por `string` preservando as chaves. Só apareceu porque rodei o `tsc`; os testes de unidade passavam, porque Vitest não faz typecheck.
**Entrega:** `messages.pt-BR.ts`, `messages.en.ts`, `type Messages = typeof ptBR`.
**Verificação:** remover uma chave do `en` quebra o `typecheck`.
**ACs:** AC-04
**Depende de:** T09

### T14 — Páginas de erro e de não encontrado ✅
**Status:** concluída em 2026-08-27 — `not-found.tsx` e `error.tsx` por segmento de idioma, com o idioma saindo do `usePathname` (o `not-found` do Next não recebe params, e resolver por cabeçalho exigiria middleware em toda requisição). `web-sem-api` no compose, sob o perfil `e2e`, com `API_BASE_URL` apontando para a porta *discard*. **19 testes e2e verdes** nos projetos `chromium` e `resiliencia` rodando em paralelo — nenhum `docker stop`.

**Dois achados:**
1. O mapeamento de erro ficou **no servidor** (`tratarErroDaApi`), não no `error.tsx`: em produção o Next apaga a mensagem antes de entregá-la ao boundary, deixando só um digest — decidir "isto é 404" lá seria decidir sem informação.
2. O job `web` do CI subia o compose **sem seed**. Passou despercebido enquanto as páginas eram placeholder; a partir desta tarefa elas resolvem slug contra a API, e a smoke testaria só a página de 404. Corrigido com `SEED_ENABLED=true` e o perfil `e2e`.
**Entrega:** `not-found.tsx` e `error.tsx` por segmento de idioma; mapeamento de `ApiError` → página (plano §DT-07); `data-testid` nas duas; **serviço `web-sem-api` no compose** — mesma imagem, `API_BASE_URL` apontando para `http://127.0.0.1:9` — que é o alvo do projeto `resiliencia`.
**Verificação:** e2e **`@smoke`**: slug inexistente → HTTP **404** (não 200). Projeto `resiliencia`: a instância sem API responde 5xx com a página de erro no idioma da rota, sem stack trace no HTML, e `detail` cru não aparece na versão em inglês. **Nenhum `docker stop` em teste** — derrubar o `app` mataria os outros workers (plano §DT-10.3).
**ACs:** AC-13, AC-18, AC-19
**Depende de:** T12, T13

---

## Fase 3 — Telas

### T15 — Home com as categorias ✅
**Status:** concluída em 2026-08-27, no commit `ca179bc` — a home lista as raízes com a contagem, as subcategorias como cards e a busca como `form` GET puro. **A linha de status faltou naquele commit e foi escrita no T19**; o cabeçalho desta tabela também estava atrasado por isso (dizia 14 concluídas com T15–T18 já fechadas). O que o commit registra: o defeito de contrato do 001 (`productCount`/`sortOrder` no OpenAPI contra `product_count`/`sort_order` servidos, e `children` sumido), corrigido na raiz com o `ModelResolver` do `ObjectMapper` da aplicação e coberto por `OpenApiFidelidadeIT`.
**Entrega:** `[locale]/page.tsx` consumindo `GET /categories`; raízes com `product_count`, subcategorias como links, campo de busca que submete para a rota de busca.
**Verificação:** e2e **`@smoke`**: a home responde no idioma certo e lista categorias (substitui o smoke placeholder de T03). e2e full: a contagem exibida bate com a da API, nos dois idiomas.
**ACs:** AC-07
**Depende de:** T11, T12, T13

### T16 — Card de produto e a lista, sem filtros ainda ✅
**Status:** concluída em 2026-08-27 — `ProdutoCard` com preço via `Intl`, badges e bloco de logo de altura fixa; página de categoria pedindo categoria e produtos **em paralelo**. 5 testes e2e, incluindo o de altura do logo e o de produto sem preço. Formatação em 4 testes de unidade.

**Terceiro defeito no contrato do 001, encontrado aqui:** a resposta 200 de `GET /products` — o endpoint mais usado — não declarava schema, só exemplo, porque `@Content` escrito à mão substitui o que o springdoc deduziria. O tipo gerado vinha como `unknown`. Corrigido, e `OpenApiFidelidadeIT` ganhou um teste que varre **todas** as respostas de sucesso do documento — 227 testes no backend.

**Dois achados sobre o seed:** nenhum dos 20 produtos tem `logo_url`, então o fallback é o caminho normal e não a exceção (o `next/image` do DT-09 segue sem ser exercitado); e três não têm preço, servidos como `null` e não ausentes.
**Entrega:** `ProductCard` (logo com dimensões e placeholder de fallback, preço formatado com `Intl`, badges), com `data-testid="produto-card"` + `data-slug`; página de categoria consumindo `GET /products?category=`.
**Verificação:** e2e **`@smoke`**: uma categoria do seed lista produtos. e2e full: os produtos batem com a API; card sem logo não desloca o layout.
**ACs:** —
**Depende de:** T15

### T17 — Filtros como `<form method="GET">` ✅
**Status:** concluída em 2026-08-27 — `lib/filters.ts` puro (**10 testes de unidade**, incluindo ida e volta da query) e `FiltroPainel` com `fieldset`/`legend` por grupo. 6 testes e2e: filtrar muda URL e lista, recarregar reproduz, os controles refletem a URL, filtros combinam, limpar zera, e valor inválido vira erro da API.

**Três decisões que valem registro:**
1. Os valores ficam como **texto cru** — o frontend não conserta `pricing_model=XPTO`, manda para a API, que já responde 400. Um só lugar valida.
2. Integração com `product_count: 0` não vira opção: são 8 das 12, e ofereceriam filtro que só produz lista vazia.
3. `q` vai num campo oculto no formulário de filtro — sem isso, filtrar dentro de uma busca apagaria a busca.
**Entrega:** `lib/filters.ts` (parse/serialize, puro) e `FiltroPainel` com `fieldset`/`legend`; opções de integração vindas de `GET /integrations`.
**Verificação:** unidade cobre ida e volta da query. e2e: filtrar muda a URL e a lista; recarregar a URL reproduz a lista.
**ACs:** AC-08
**Depende de:** T16

### T18 — Sem JavaScript continua funcionando ✅
**Status:** concluída em 2026-08-27 — 5 casos `@no-js` reais (navegar, filtrar, controles refletindo a URL, limpar, buscar da home) mais a melhoria progressiva: com JavaScript o painel submete sozinho e o botão "aplicar" some por CSS, depois que o script marca o formulário. Suíte completa nos cinco projetos: **107 testes**.

**Dois defeitos meus que só apareceram aqui:**
1. **Os projetos com JavaScript ligado rodavam os casos `@no-js`.** O `chromium-no-js` filtra por `grep`, mas `chromium`, `webkit` e `mobile-chromium` não excluíam — então rodavam com o motor ligado casos que afirmam o contrário, e falhavam por estarem certos. Faltava `grepInvert` na config do T03.
2. O teste de filtro do T17 clicava no botão "aplicar", que a partir desta tarefa está oculto quando há JS. Passou a marcar a caixa e afirmar a URL; a versão com botão é o caso `@no-js`.
**Entrega:** o projeto `chromium-no-js` do Playwright passa a ter casos reais; botão "aplicar" visível sem JS e escondido com JS.
**Verificação:** com JS off, aplicar filtro e paginar funcionam.
**ACs:** AC-09
**Depende de:** T17

### T19 — Paginação com links reais ✅
**Status:** concluída em 2026-08-27 — `lib/paginacao.ts` puro (**8 testes de unidade**, contando os três de `comPagina`) e o componente `Paginacao` com dois `<a rel="prev"/"next">`. Suíte e2e nos cinco projetos: **129 testes** (eram 107), sendo 7 novos de paginação e 1 `@no-js`. `npm run verify` verde: **46 testes de unidade**. Smoke segue em 3 casos, e o `e2e:isolamento` passa (80 execuções).

**Três decisões que valem registro:**
1. **A página na URL é a página da API: base 0.** Mostrar `?page=1` para a primeira exigiria subtrair um antes de chamar a API, e isso é exatamente a tradução de valor que o T17 decidiu não ter — com ela, `page=0` e `page=1` viram a mesma tela para quem digita a URL, e o 400 da API chega deslocado. O rótulo é base 1 (“Página 2 de 3”); só a URL é base 0.
2. **A primeira página não carrega `page=`.** Duas URLs para a mesma lista fariam o T25 escolher canônica depois do fato.
3. **`size` padrão 12, e a URL continua mandando.** Com os 20 do padrão da API a paginação nunca apareceria no seed — caminho que nenhum teste percorre é caminho que não existe (plano, risco 5). `?size=` do visitante prevalece, inclusive inválido, para o 400 vir da API.

**Defeito na varredura, corrigido aqui:** `check-testids.mjs` acusava o `<a href>` **escrito no comentário** do componente. A varredura passava a ditar o que se pode escrever em prosa; agora ela zera os comentários de bloco antes de procurar (preservando as linhas). Conferido dos dois lados: o gatilho de verdade (`<a>` sem id) continua falhando, e o comentário não falha mais.

**Achado no backend, corrigido no T28:** com o offset além do fim, `GET /products` respondia `total: 0`, e não o total real (o `total` viajava em `count(*) over ()` nas linhas, e página vazia não tem linha). Enquanto isso valeu, a tela não tinha como oferecer “anterior” em `?page=90`, e o e2e afirmava só o que era verdade: 200, estado vazio e saída pelo “limpar”. **Com o T28 fechado, o teste passou a afirmar o link de “anterior” apontando para a última página que existe** — que é o que `paginacaoDe` sempre soube fazer, e não podia provar.
**Entrega:** `<a>` de página com `rel="prev"/"next"`; `size` pequeno para o seed de 20 produtos render mais de uma página.
**Verificação:** e2e navega para a página 2 e volta pelo botão do navegador, chegando na mesma lista filtrada.
**ACs:** AC-10, AC-09 (a outra metade: “e paginar”, que o T18 não tinha como cobrir)
**Depende de:** T17

### T20 — Busca, com o aviso de resultado aproximado ✅
**Status:** concluída em 2026-08-27 — a rota de busca passou a existir de verdade (era placeholder), e a lista virou um componente só. **8 testes e2e novos**; suíte completa nos cinco projetos: **153 testes** (eram 129). `npm run verify` verde: 46 testes de unidade.

**"Reusando a lista" virou reuso mesmo:** `ListaComFiltros` concentra contagem, cards, painel de filtro e paginação, e as duas páginas passam só o cabeçalho e o `base`. Copiar o bloco para a busca teria funcionado hoje e divergido na primeira correção — e a cópia esquecida seria a menos visitada.

**Busca sem termo não chama a API.** `GET /products` sem `q` devolveria o catálogo inteiro, e uma lista de tudo sob o título "resultados para" é uma resposta errada com cara de certa. Sem termo, a página mostra o campo e um convite, com status 200.

**Os termos do e2e vêm do seed, e foram medidos contra a API antes de virar teste:** `zapier` casa por `FULL_TEXT`, `zapierr` (um caractere a mais) cai no `TRIGRAM` com 1 resultado, e `chatbo` responde `NONE` — que é "nada casou", e não "casou por aproximação". O aviso só aparece no caso do meio.
**Entrega:** rota de busca reusando a lista; aviso quando `query.matched_by === "TRIGRAM"`.
**Verificação:** e2e busca com erro de digitação de 1 caractere e vê o aviso; busca exata não mostra.
**Também coberto:** o aviso no idioma da rota, paginar e filtrar dentro da busca sem perder o `q`, o campo voltando preenchido, busca sem termo e busca sem resultado (as duas com 200).
**ACs:** AC-11
**Depende de:** T17

### T21 — Estado vazio que oferece saída ✅
**Status:** concluída em 2026-08-27 — `ListaVazia` com os filtros aplicados como chips removíveis, "limpar tudo" e as subcategorias como alternativa. **6 testes de unidade** em `filtro-rotulos.ts` e **6 e2e** (um deles `@no-js`). Suíte completa: **169 testes** (eram 153); `verify` verde com 52 de unidade.

**Cada remoção é um `<a href>` montado por `semValor`** — a mesma função que o T17 já cobre, que também zera a página. Nada de botão com handler: a saída do beco não pode depender de script.

**A árvore de categorias é buscada só quando a lista vem vazia**, e dentro de `ListaComFiltros`, não em cada página: lista cheia não paga a ida à API, e as duas portas de entrada ganham a mesma saída sem ninguém precisar lembrar.

**O rótulo do chip é o mesmo texto do controle no painel** ("Tem plano gratuito", e não "Plano gratuito"): dois textos para o mesmo critério fariam a remoção parecer outro filtro. Integração sem nome no catálogo cai no slug cru — filtro sem rótulo é filtro que não dá para remover.

**A combinação do e2e foi escolhida contra a API:** em `agentes-e-workflows`, `has_free_plan=true` traz 3 e `price_max=1` zera. Com os dois, o teste de "remover um critério" prova que saiu **um**, e não os dois — o que a primeira escolha (`atendimento-e-chatbots`, onde o `has_free_plan` sozinho já dá vazio) não provaria.
**Entrega:** vazio mostrando os filtros aplicados, botão para limpar cada um e as categorias como alternativa; `data-testid="lista-vazia"`.
**Verificação:** e2e chega a zero resultados e confere **status 200** e a presença dos controles de limpar.
**ACs:** AC-12
**Depende de:** T17

### T22 — Ficha do produto ✅
**Status:** concluída em 2026-08-27 — `FichaProduto` com todos os campos da spec §5.3. **6 testes de componente** (os primeiros do projeto — Vitest + Testing Library), **6 e2e** (um `@smoke`, um `@no-js`). Suíte completa: **185 testes** (eram 169); `verify` verde com **58** de unidade/componente. A smoke foi para **4 casos**.

**O aviso de tradução ausente é acionado por `pros`, `cons`, `best_for` e `faq` vazios — a `description` fica de fora da conta:** ela vem da ficha básica, não da curadoria, e um produto com descrição e sem curadoria continua sendo um produto sem conteúdo curado.

**Idioma e país saem do `Intl.DisplayNames`**, não de uma tabela nossa: `en` vira "inglês"/"English" e `US` vira "Estados Unidos"/"United States" conforme a rota. Tabela escrita à mão aqui seria dado de produto de cabeça (constituição §7) e envelheceria. Código desconhecido volta cru — melhor `ZZ` na tela do que esconder que o dado existe.

**O link externo carrega `rel="nofollow sponsored noopener"` e é afirmado nos dois lados** (componente e e2e): `nofollow sponsored` porque o catálogo é comercial e o link não é recomendação editorial, `noopener` porque `target="_blank"` sem ele entrega o `window.opener` ao destino.

**Achado do catálogo de textos:** "Tags" é igual nos dois idiomas e o teste de paridade do T13 acusou. Virou exceção explícita, com o motivo — "etiquetas" existe no dicionário e não é como se chama isso num catálogo de software. É a segunda exceção da lista; a primeira é "Freemium".
**Entrega:** todos os campos da spec §5.3; link externo com `rel="nofollow sponsored noopener"`; "atualizado em"; aviso de tradução ausente quando os campos longos vierem vazios.
**Verificação:** e2e **`@smoke`**: a ficha abre pelo slug. e2e full: a ficha nos dois idiomas, com os campos longos preenchidos — o que o seed tem. **Componente (Vitest):** props com `pros`, `cons`, `best_for` e `faq` vazios renderizam o aviso de tradução ausente sem quebrar. Não é e2e por decisão registrada: nenhum produto publicado pode ter tradução faltando (`ProductPublicationValidator` do 001), e criar um exigiria escrita via admin (plano §DT-10.4).
**ACs:** AC-14
**Depende de:** T14, T16

### T23 — Seletor de idioma que preserva a página ✅
**Status:** concluída em 2026-08-27 — `SeletorIdioma` na home, na categoria, na busca e na ficha. A regra de qual endereço é o equivalente virou função pura (`idioma-alternativo.ts`) com **6 testes de unidade**, mais **7 e2e** (um `@smoke`, um `@no-js`). `verify` verde com **64** testes; suíte completa: **204** (eram 185). **A smoke chegou aos 5 casos previstos**, em ~1 s.

**É um link, e não um `<select>` com JavaScript.** São dois idiomas: a troca é sempre "ir para a outra versão". Link funciona sem script, o robô segue e o `hreflang` diz para onde vai. O teste usa `data-locale`, e não o texto do link — que é justamente o que muda de idioma.

**Cada página sabe uma coisa diferente sobre o próprio endereço equivalente**, e por isso o destino vem dela: produto usa o mesmo slug (spec 001 §4.2); **categoria depende do `alternates` da API** (o slug muda, e adivinhar daria 404 silencioso — foi para isso que o T07 existiu); busca leva a query junto.

**Os filtros atravessam a troca de idioma.** Os valores são os da API (slug de integração, nome de enum, preço), iguais nos dois idiomas — deixá-los para trás faria a troca zerar a busca sem avisar.

**A home é fallback, e só isso:** categoria sem par no `alternates` vai para a home do outro idioma, que é o único caso em que a troca perde o lugar. Coberto por unidade, porque o seed não tem categoria sem par — e não deveria ter.
**Entrega:** `SeletorIdioma` usando `alternates`; fallback para a home quando não houver par.
**Verificação:** e2e **`@smoke`**: trocar o idioma na ficha leva ao mesmo produto. e2e full: idem na categoria e o caso sem par.
**ACs:** AC-05
**Depende de:** T07, T22

### T24 — Comparação ✅
**Status:** concluída em 2026-08-27 — seleção acumulada na URL da lista, tabela com a primeira coluna fixa, e os dois casos de erro tratados como tela. **6 testes de unidade** e **10 e2e** (um `@no-js`). Suíte completa: **232** (eram 204); `verify` verde com **70**. Fecha a Fase 3.

**A seleção virou estado de URL de primeira classe**, e não um parâmetro solto: `comparar` entrou no `FiltroCatalogo` como *campo local* — vai para a URL, **não** vai para a query da API. Com isso todo gerador de link que já existia (paginação, remover filtro, trocar de idioma) preserva a seleção de graça. Sem essa marca, marcar três produtos e virar a página apagaria a seleção sem avisar, e cada gerador de link precisaria lembrar de copiar mais um parâmetro.

**Passar do limite não empurra o mais antigo para fora.** O quarto card mostra "máximo de 3 produtos" em vez do controle; remover em silêncio algo que a pessoa escolheu é pior do que não aceitar o quarto.

**Os dois erros são tela, e não erro:** faixa inválida nem chega a chamar a API (a página pede de 2 a 3, com 200), e `missing_slugs` vira "não encontramos: X" com a opção de comparar os que restaram. O `detail` do Problem Details **nunca** vai para a tela — vem no idioma da API e apareceria cru em página inglesa (AC-19, coberto por e2e).

**Defeito de layout que só o e2e pegou:** o link do card tinha `h-full` e cobria o controle de comparar logo abaixo — o clique caía no card e ia para a ficha. A olho nu a tela parecia certa. Agora quem ocupa a altura da célula é o `<li>`, e o link do card é `flex-1`.

**O `placeholder.tsx` foi removido:** com a comparação, todas as dez rotas do `app/` têm tela real. Ele já dizia no próprio comentário que sumiria conforme as telas entrassem.
**Entrega:** seleção acumulada na URL (máximo 3), tabela com primeira coluna fixa no mobile, tratamento de faixa inválida e de `missing_slugs`.
**Verificação:** e2e compara 3; com 1 slug vê o aviso de faixa (sem 500); com slug inexistente vê quais faltaram.
**ACs:** AC-16, AC-17
**Depende de:** T22

---

## Fase 4 — Descoberta e qualidade

### T25 — Metadata, hreflang e JSON-LD ✅
**Status:** concluída em 2026-08-27 — `generateMetadata` nas dez rotas, `lib/seo/` com `urls.ts`, `metadata.ts` e `jsonld.ts`. **7 testes de unidade** e **9 e2e**. `verify` verde com **77**; suíte completa: **259** (eram 232).

**A canônica é absoluta porque o AC-06 pede, e o valor vem do ambiente:** `SITE_URL` entrou no `.env.example` e no compose. Caminho relativo em `rel=canonical` é ambíguo quando a mesma página responde em mais de um host (preview, domínio antigo, IP). Ao contrário de `API_BASE_URL`, esta variável é **pública de propósito** — ela sai no HTML.

**O `hreflang` é recíproco nas duas versões**, e é isso que o e2e afirma: uma seta só o rastreador ignora. `x-default` aponta para pt-BR, o mesmo destino do redirect da raiz.

**Na categoria o par vem do `alternates`** (T07) e não de traduzir o slug à mão; no produto sai do próprio slug com o segmento traduzido, sem chamada extra — como o plano previu.

**Duas decisões de indexação que a spec não detalhava:**
1. **A canônica da busca não carrega a query.** Cada termo digitado viraria uma URL nova sem conteúdo próprio.
2. **A comparação é a única página fora do índice** (`noindex, follow`): ela é uma consulta montada pelo visitante, e cada combinação de slugs seria uma URL sem conteúdo próprio. Os links para as fichas continuam sendo seguidos.

**JSON-LD só afirma o que existe:** produto sem preço publicado não ganha bloco `offers` — preço "sob consulta" é diferente de preço zero, e anunciar um preço que não existe é falso. Produto sem categoria pula o degrau da trilha em vez de emitir um item vazio.

**Custo não medido:** `generateMetadata` e a página pedem a mesma ficha. Se o Next memoiza o `fetch` dentro de uma renderização, é uma chamada só; **não medi** (a API não loga requisição, então a contagem que tentei deu zero dos dois lados e não prova nada). O teto é uma chamada extra por página renderizada.
**Entrega:** `generateMetadata` por rota (canônica absoluta, `alternates.languages`, `x-default`); `lib/seo/jsonld.ts` com `SoftwareApplication` e `BreadcrumbList`; `noindex` na comparação.
**Verificação:** unidade valida o shape do JSON-LD; e2e confere `hreflang` recíproco nas duas versões e a canônica.
**ACs:** AC-06, AC-15
**Depende de:** T23

### T26 — `sitemap.xml` e `robots.txt` ✅
**Status:** concluída em 2026-08-27 — `app/sitemap.ts` e `app/robots.ts`, com **6 testes e2e**. O sitemap servido tem **52 URLs**: (1 home + 5 categorias + 20 produtos) × 2 idiomas. Suíte completa: **277** (eram 259).

**`force-dynamic` nas duas rotas, e não é detalhe:** sem isso o Next tentaria pré-renderizá-las durante o `npm run build`, e **no CI o build roda antes de o compose subir** — o sitemap sairia vazio, ou o build quebraria, por um motivo que nada na tela denunciaria. Verificado do jeito que importa: `API_BASE_URL=http://127.0.0.1:9 npm run build` passa, e o relatório do build marca as duas rotas como `ƒ` (dinâmicas).

**Cada página entra duas vezes, uma por idioma, e cada entrada declara as duas versões** em `alternates.languages` (que o Next serializa como `xhtml:link`). A redundância é de propósito: a entrada separada garante que as duas URLs sejam descobertas, e o `alternates` diz que são a mesma coisa em idiomas diferentes.

**A categoria custa uma ficha por categoria** — o slug muda com o idioma e quem tem o par é a API. São 5 no 001; é o preço de não adivinhar slug. Produto não custa nada: o slug é o mesmo nos dois idiomas.

**A comparação não entra no `Disallow` do robots, embora seja `noindex`:** bloquear o rastreamento impediria o robô de ler justamente a diretiva que pede para não indexar. Coberto por e2e, porque é o tipo de "otimização" que alguém adiciona de boa-fé depois.
**Entrega:** `app/sitemap.ts` percorrendo categorias e produtos publicados nos dois idiomas; `app/robots.ts`.
**Verificação:** e2e conta as URLs do sitemap contra o total da API e confere que cada uma aparece nos dois idiomas.
**ACs:** AC-21
**Depende de:** T25

### T27 — Fechamento: axe, varredura de seed e README ✅
**Status:** concluída em 2026-08-27 — axe em seis páginas, `check-seed-leak.mjs` no `verify`, a varredura de vazamento de `API_BASE_URL` como e2e, e o README com a seção do frontend. Suíte completa: **310** (eram 277); `verify` verde com 77; `./mvnw verify` verde com 229. Fecha a fatia.

**O axe encontrou um defeito real, e crítico:** o campo "preço até" estava sozinho dentro de um `fieldset`/`legend`. A legenda nomeia o **grupo**, não o campo — então o input não tinha nome acessível, e quem navega por leitor de tela ouvia "editar texto" e mais nada. Virou `label`/`htmlFor`. Três páginas acusavam a mesma violação, e nenhuma revisão visual pegaria: na tela estava tudo certo.

**O corte do axe é `critical` e `serious`.** As faixas `moderate` e `minor` incluem muita heurística de contraste e de landmark; travar o build nelas treina quem lê a ignorar o relatório inteiro. O axe fica **fora da smoke** — ele carrega e roda um motor de análise por página, e o orçamento da smoke é de 5 min para cinco casos.

**A varredura de seed nasceu com dois falsos positivos, os dois instrutivos:**
1. o seed tem o produto `descript` (Descript), e busca por substring casava com a palavra `description` em quinze lugares legítimos — passou a exigir **borda de palavra**;
2. explicar um comportamento citando "Zapier" num comentário virava violação. Comentário não é dado hardcoded, e varredura que proíbe exemplo em prosa passa a ditar como se escreve — mesma lição do `check-testids` no T19. Comentários são zerados antes da varredura.

Conferida dos dois lados: com `"Automação de WhatsApp"` escrito na home, ela falha apontando arquivo e linha; sem, passa com 49 termos conferidos.

**O AC-24 é verificado de fora, e não no código-fonte:** o teste pede o HTML **servido** e depois baixa cada `<script src>` da página, procurando `http://app:8080`, `API_BASE_URL` e `ADMIN_PASSWORD`. A guarda real é o `server-only` (T12), que quebra o build; este teste cobre o que ela não pega — o valor vazando pelo payload de hidratação. Tem contraprova: o teste que afirma que a URL **pública** aparece, senão um HTML sem URL nenhuma passaria por engano.

**AC-01 remedido com a stack inteira (2026-08-27):** `down -v` + `up --build --wait` em **1 min 57 s** com cache quente, quatro serviços saudáveis, `web` como não-root (1001), 20 produtos e 52 URLs no sitemap. **A raiz responde 307, e não 200** — ela redireciona para o idioma (AC-03); quem verificar com `curl` precisa de `-L`. Está anotado no README, porque é o tipo de "falha" que assusta quem confere pela primeira vez.

**Dependência:** `@axe-core/playwright@4.13.0` — a linha já existia no plano §DT-02 desde o início. Registrado lá também o que **não** foi instalado (`class-variance-authority`, `@radix-ui/*`, `lucide-react`): nenhuma tela precisou de primitiva com estado nem de ícone.
**Objetivo:** os ACs que são regra, não tela — e o DoD da constituição §8.
**Entrega:** axe no Playwright em home/categoria/ficha, dentro da full; `scripts/check-seed-leak.mjs` varrendo `web/` contra os slugs e nomes do seed (AC-22); verificação de que `API_BASE_URL` não aparece no HTML servido nem no bundle (AC-24, risco 2 do plano); `README.md` da raiz com a seção do frontend, a verificação manual de AC-01 e como rodar smoke e full.
**Verificação:** axe sem violação crítica ou séria; as varreduras verdes; `docker compose up --build` do zero conferido e o tempo anotado no README.
**ACs:** AC-20, AC-22, AC-24, AC-01 (manual)
**Depende de:** T26

---

## Fase 5 — o que o frontend encontrou no backend

### T28 — `total` correto quando o offset passa do fim ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **229 testes**, 5 novos (2 de integração, 3 de unidade no montador). A contagem saiu das linhas e virou consulta própria, com os mesmos filtros e a mesma junção da página.

**O defeito era maior do que o registrado no T19:** quem decidia se *houve casamento* era a página ter vindo com linha. Então uma busca legítima na página 90 não só perdia o `total` — era relatada como `matched_by: NONE`, e ainda disparava o fallback de similaridade por nada. Agora quem decide é a contagem, e o `matched_by` sobrevive ao offset.

**Preço:** uma ida a mais ao banco por busca (contagem + página, em vez de só a página). É a forma padrão de paginar por offset, e o alternativo — manter o `count(*) over ()` e só contar de novo quando a página vem vazia — deixaria duas maneiras de calcular o mesmo número, que divergem na primeira mudança de filtro.

**Conferido dos dois lados:** com o código de produção revertido (e só ele), os dois testes de integração falham — `total` vem `0` onde deveria vir `5`, e o `matched_by` vem `NONE` onde deveria vir `FULL_TEXT`. Com a correção, passam.
**Objetivo:** o AC-05 do 001 pede `total` correto, e hoje ele é `0` em qualquer página além da última — `GET /products?page=90` devolve `{ items: [], total: 0 }` num filtro com 20 resultados. Encontrado no T19.
**Causa:** `SearchSqlBuilder` carrega o total em `count(*) over ()`, uma coluna das linhas retornadas. Sem linha, não há total. O valor está certo em toda página que existe, e some exatamente onde o cliente mais precisa dele para se orientar.
**Entrega:** contagem numa consulta própria, com os mesmos filtros e junções da busca (ou equivalente que não dependa de haver linha); `ProductListResponse` mantém a forma.
**Verificação:** teste de integração: com 20 produtos publicados e `size=12`, `page=2` responde `items` vazio e `total: 20`; a página 0 continua com o mesmo total de hoje. Depois disso, o e2e “página fora da faixa” do T19 passa a poder afirmar o link de “anterior”. **Feito:** o e2e do T19 foi atualizado no commit seguinte, com o compose rodando a API corrigida — 169 e2e verdes.

**O que este push NÃO fechou:** a verificação em aberto do T04 (“push só de backend não dispara o `web`”). A branch era nova, e branch nova não tem base comparável — o classificador cai no “roda tudo”, de propósito. **E o merge para a `dev` também não fechou**, ao contrário do que eu tinha previsto aqui: o PR levava junto o commit que atualizou o e2e do T19, então o push para a `dev` tinha `web/` no diff e o job `web` rodou inteiro (run `33075293349`). Registrado no T04, com a condição que de fato fecha.
**ACs:** AC-05 do 001
**Depende de:** —

---

## Ordem sugerida de branches

| Branch | Tarefas |
|---|---|
| `feature/frontend-fundacao` | T00–T06 |
| `feature/alternates-api` | T07, T08 |
| `feature/frontend-nucleo` | T09–T14 |
| `feature/frontend-telas` | T15–T24 |
| `feature/frontend-seo` | T25–T27 |
| `feature/total-fora-da-faixa` | T28 (backend, independente) |

Cada branch abre PR automático para `dev`, conforme o fluxo que o 001 já usa.

## Os cinco casos da smoke

Fixos, e cada um nasce numa tarefa diferente:

| Caso | Nasce em |
|---|---|
| Home responde 200 no idioma da rota | T15 (placeholder em T03) |
| Categoria lista produtos do seed | T16 |
| Ficha abre pelo slug | T22 |
| Seletor de idioma leva ao mesmo produto | T23 |
| Slug inexistente responde 404 | T14 |
