# Plan 002 — Frontend do catálogo

| Campo | Valor |
|---|---|
| Spec | `specs/002-frontend-catalogo/spec.md` (Aprovada, 2026-08-26) |
| Constituição | `CLAUDE.md` (raiz) |
| Data | 2026-08-26 |
| Artefato novo | `web/` — aplicação Next.js no mesmo repositório |
| Toca no backend | Sim, em dois pontos: campo `alternates` (spec §4.1) e CORS de desenvolvimento |

## 0. O que a spec já fechou (não reabrir aqui)

Monorepo com o frontend em `web/`; Next.js App Router com SSR; i18n por rota com prefixo nos dois idiomas; estado de navegação na URL; Tailwind + shadcn/ui sobre Radix; Vitest e Playwright; produção em container próprio com `output: standalone`; campo `alternates` como desvio aprovado sobre o 001.

## 1. Decisões técnicas

### DT-01 — Runtime e build

Node 22 LTS. **Next.js 16.3.3** com App Router e React 19.2.8 — o plano foi escrito supondo a 15, e o esqueleto (T01) pinou o que o gerador oficial entrega hoje. `output: "standalone"` — o Dockerfile copia `.next/standalone` e o build final não carrega `node_modules` inteiro. Multi-stage igual ao backend, usuário não-root, mesma disciplina do Dockerfile do 001.

Versões pinadas em 2026-08-27, no T01: deixei o `create-next-app` escolher o conjunto em vez de pedir a última major de cada pacote — ele fixou `eslint@9` e `typescript@5` mesmo com a 10 e a 7 publicadas, que é o conjunto que o `eslint-config-next@16` realmente suporta.

`npm run typecheck` é `next typegen && tsc --noEmit`, e não só o `tsc`. O Next 16 gera tipos de rota e de layout (`LayoutProps<"/">` e afins) dentro de `.next/types/`, então `tsc` sozinho passa na máquina de quem já rodou o build e falha no CI, que parte de um checkout limpo. Descoberto exatamente assim, no primeiro run do job `web`.

Gerenciador de pacotes: **npm**, com `package-lock.json` versionado. Não porque seja o melhor, mas porque é o que vem com o Node e não adiciona ferramenta a instalar antes do `docker compose up` funcionar — que é o critério da constituição §8.

### DT-02 — Dependências (constituição §7.1 exige justificativa)

| Pacote | Para quê | Por que não sem ele |
|---|---|---|
| `next`, `react`, `react-dom` | O framework | Constituição §3 |
| `typescript`, `@types/*` | Tipagem | O contrato da API é gerado; sem TS o ganho evapora |
| `tailwindcss`, `postcss`, `autoprefixer` | Estilo | Spec §8.1 |
| `class-variance-authority`, `clsx`, `tailwind-merge` | Variantes de componente | Vêm com o shadcn; escrever à mão é reimplementá-los pior |
| `@radix-ui/react-*` | Primitivas acessíveis (select, dialog, checkbox) | Teclado + ARIA corretos; AC-20 depende disso |
| `lucide-react` | Ícones | Ícone como componente com `aria-hidden` correto |
| `openapi-typescript` (dev) | Tipos a partir do `/v3/api-docs` | AC-23 |
| `vitest`, `@testing-library/react`, `jsdom` (dev) | Unidade | Regras puras sem subir navegador |
| `@vitejs/plugin-react` (dev) | Transformar JSX para o Vitest | Sem ele o Vitest não compila componente nenhum |
| `@testing-library/dom` (dev) | Peer da Testing Library 16 | Deixou de vir junto na 16; sem instalar explícito, não resolve |
| `@testing-library/jest-dom` (dev) | `toBeInTheDocument` e afins | Asserção de DOM legível; a alternativa é comparar nó a nó |
| `eslint-config-prettier` (dev) | Desliga as regras de formato do ESLint | Duas ferramentas discutindo indentação produz diff eterno |
| `server-only` | Quebra o build se o cliente da API for importado do navegador | É o que mantém `API_BASE_URL` fora do bundle (AC-24); revisão humana não pega isso de forma confiável |
| `@playwright/test` (dev) | Fluxo, inclusive com JS off | AC-09 só existe num navegador real |
| `@axe-core/playwright` (dev) | Acessibilidade automatizada | AC-20 |
| `eslint`, `eslint-config-next`, `prettier` (dev) | Análise estática | Constituição §8: "análise estática configurada" |

Instalado no T27: `@axe-core/playwright@4.13.0` — a linha já existia nesta tabela desde o plano, e o pacote entrou quando o AC-20 virou teste. As linhas de `class-variance-authority`, `@radix-ui/*` e `lucide-react` seguem **previstas e não instaladas**: nenhuma tela desta fatia precisou de primitiva com estado (select, dialog) nem de ícone — o que existe é formulário nativo e link. Entram quando uma tela pedir, não antes.

Nada além disso entra sem uma linha nova nesta tabela, no mesmo commit.

### DT-03 — Camada de acesso à API: um cliente só, no servidor

`web/src/lib/api/` com:

- `types.gen.ts` — **gerado** por `npm run api:types`, nunca editado à mão. Cabeçalho com aviso.
- `client.ts` — um `fetch` tipado que recebe rota, query e `AppLocale`; injeta `Accept-Language`, aplica `timeout` (AbortSignal), e converte resposta não-2xx em `ApiError` carregando o Problem Details já parseado.
- `catalog.ts` — funções nomeadas por caso de uso (`listarProdutos`, `produtoPorSlug`, `arvoreDeCategorias`, `compararProdutos`, `listarIntegracoes`). As páginas chamam essas, nunca o `client` direto.

Regras: o módulo inteiro é `server-only` (marcado com o pacote `server-only`, que é do Next e falha o build se importado no cliente). Nenhum componente de cliente importa daqui — é assim que `API_BASE_URL` fica fora do bundle (AC-24).

**Campo opcional no tipo gerado pode ser `null` em tempo de execução.** A API usa `default-property-inclusion: always`, então campo sem valor vai no JSON como `null`; o OpenAPI, por outro lado, não marca anuláveis, e o tipo gerado diz `number | undefined`. As duas coisas descrevem verdades diferentes — o tipo descreve a *forma*, não a nulidade. Regra prática no frontend: comparar com `== null`, que cobre os dois, e nunca com `=== undefined`. Descoberto em `T16`, num teste que procurava produto sem preço e não achava nenhum, embora existam três.

### DT-04 — Idioma: um tipo, resolvido na borda

`AppLocale = "pt-BR" | "en"`. O `layout.tsx` de `[locale]` valida o segmento e chama `notFound()` para qualquer outro valor — `/fr/...` é 404, não fallback silencioso.

Os **segmentos traduzidos** (`produtos`/`products`) não podem virar rotas dinâmicas sem perder tipagem, então cada par vira uma rota própria no `app/`, e um mapa único (`routes.ts`) traduz `(pagina, locale) → caminho`. Toda geração de link passa por esse mapa; link montado com template string é erro de revisão.

`middleware.ts` cuida apenas de `/` → redirect 307 conforme `Accept-Language` (AC-03). Não faz mais nada: middleware é o lugar mais caro para colocar lógica no Next.

### DT-05 — Filtros: a URL é o estado, o `<form>` é o mecanismo

Os filtros vivem num `<form method="GET">` cujos `name` são exatamente os parâmetros da API (`integration`, `pricing_model`, `has_free_trial`, `has_free_plan`, `price_max`). Sem JS o navegador monta a query sozinho — é daí que AC-09 sai de graça, e não de um caminho alternativo mantido em paralelo.

Com JS, um componente de cliente pequeno faz `form.requestSubmit()` no `change` e usa `router.replace` para não empilhar histórico a cada clique de checkbox. O botão "aplicar" continua no DOM, escondido só quando o JS carrega (`noscript`-invertido via CSS), porque some-lo antes disso quebraria a versão sem JS.

Parsing e serialização da query ficam em `lib/filters.ts`, puro e testado em Vitest: `searchParams → FiltroCatalogo → query da API` e a volta. Parâmetro desconhecido é ignorado; valor inválido (ex.: `pricing_model=XPTO`) não é "corrigido" no frontend — vai para a API, que já responde 400, e a página mostra o erro de parâmetro. Um só lugar valida.

### DT-06 — `alternates` e o desvio no backend

No backend (módulo `catalog`), **só** a resposta de `GET /categories/{slug}` ganha `alternates: Map<String, String>` — produto tem um slug só, e repeti-lo nos dois idiomas seria ruído no contrato. A fonte são as colunas `slug_pt`/`slug_en` já existentes em `category` — **projeção, não migração** (constituição §5: migração commitada é imutável, e aqui não há nenhuma a criar).

O campo **não** pode entrar em `CategoryNodeView`, que é o DTO da árvore: `application.yml` define `default-property-inclusion: always`, então um campo nulo apareceria como `"alternates": null` em cada nó de `GET /categories`. A rota de detalhe ganha um DTO próprio.

No frontend, `alternates` alimenta duas coisas na página de categoria: o `href` do seletor de idioma e o `<link rel="alternate" hreflang>`. Na ficha de produto, as duas saem do mesmo slug com o segmento de rota traduzido pelo `lib/routes.ts` — sem chamada extra.

A tarefa que faz isso altera `specs/001-catalogo-core/spec.md` §5.1 no mesmo commit. Sem isso a spec 001 passa a mentir sobre o próprio contrato.

### DT-07 — Erros e estados

Três caminhos, e nenhum deles é "página em branco com 200":

| Situação | Tratamento |
|---|---|
| 404 da API (slug desconhecido, DRAFT) | `notFound()` → `not-found.tsx` do segmento, HTTP 404 (AC-13) |
| 400 da API (parâmetro inválido) | Página da lista renderiza com aviso de filtro inválido e o `detail` da API, que já vem no idioma certo |
| 5xx, timeout, API fora | `error.tsx` do segmento, HTTP 5xx, texto do catálogo do frontend. Nunca stack trace (AC-18) |

O `detail` do Problem Details só é exibido nos casos em que acrescenta informação ao visitante (qual parâmetro, quais slugs faltaram). Mensagem de sistema é do frontend. AC-19 verifica que nada cru vaza para a página em inglês.

### DT-08 — SEO como código, não como checklist

- `generateMetadata` por rota: `title`, `description`, canônica absoluta (de `SITE_URL`), `alternates.languages` com os dois idiomas (daí sai o `hreflang`) e `x-default` em pt-BR.
- JSON-LD como `<script type="application/ld+json">` renderizado no servidor: `SoftwareApplication` na ficha, `BreadcrumbList` na categoria. Montado por funções puras em `lib/seo/`, testadas em Vitest contra o shape esperado (AC-15).
- `app/sitemap.ts` percorre a árvore de categorias e a lista paginada de produtos publicados, emitindo as duas versões de cada URL (AC-21). `app/robots.ts` libera tudo e aponta o sitemap.
- `noindex` na página de comparação: é combinatória, geraria URL infinita para o rastreador.

### DT-09 — Imagens

`next/image` com loader padrão e `remotePatterns` restrito aos domínios de logo presentes no seed — allowlist explícita, porque um `remotePatterns: **` transforma o servidor em proxy de imagem aberto. `width`/`height` sempre declarados (orçamento de CLS da spec §7). Logo que falhar em carregar cai num placeholder com a inicial do produto; card sem logo não pode colapsar o layout.

### DT-10 — Testes

| Nível | Ferramenta | O que cobre |
|---|---|---|
| Unidade | Vitest | `lib/filters`, `lib/routes`, `lib/seo`, formatação de preço e de data, mapeamento de `ApiError` para página |
| Componente | Vitest + Testing Library | Card de produto, controles de filtro, aviso de tradução ausente |
| Fluxo | Playwright | Todos os ACs de navegação; um projeto com `javaScriptEnabled: false` para AC-09 |
| Acessibilidade | axe via Playwright | Home, categoria e ficha (AC-20) |

Playwright roda contra o **compose inteiro** (db + api + web) com o seed do 001 — nada de mock de API nos testes de aceite, pelo mesmo motivo que a constituição §7.3 proíbe H2: um teste que não fala com a coisa real não prova que o AC passou. Testes de unidade não sobem nada.

### DT-10.1 — Smoke e full: uma suíte, um recorte por tag

A spec §7.1 exige e2e desde a primeira tarefa, com dois gatilhos. O erro a evitar aqui é manter **duas suítes**: a smoke divergiria da full em uma semana. Então é uma suíte só, e a smoke é um `--grep @smoke` sobre ela.

```ts
test('a ficha abre pelo slug', { tag: '@smoke' }, async ({ page }) => { … })
```

| | Smoke | Full |
|---|---|---|
| Gatilho | todo push (job `web` do CI) | `workflow_dispatch` e antes de release para `main` |
| Recorte | `--grep @smoke` | tudo |
| Projetos Playwright | `chromium` | `chromium`, `webkit`, `chromium-no-js`, `mobile-chromium` |
| axe | não | sim |
| Orçamento | ≤ 5 min, falha se estourar (`globalTimeout`) | sem limite |
| Retry | 1 | 2 |

O que entra na smoke é o caminho crítico, e ele não cresce com o número de telas: home responde 200 no idioma certo, categoria lista produtos do seed, ficha abre pelo slug, seletor de idioma leva ao mesmo produto, slug inexistente responde 404. Cinco testes. Tarefa que quiser marcar um sexto precisa justificar no commit — orçamento de 5 min é o freio.

Nas duas, `webServer` do Playwright **não** sobe a aplicação: quem sobe é `docker compose up -d --wait`, porque o alvo do teste é o artefato de produção, não o `next dev`.

### DT-10.3 — Independência e paralelismo

A spec §7.2 exige que cada teste rode sozinho, em qualquer ordem e em qualquer worker. Três coisas fazem isso valer, e nenhuma delas é disciplina:

**1. A suíte não escreve, então não há o que isolar.** `fullyParallel: true`, `workers: '50%'` local e `2` no CI. O seed do 001 é fixture compartilhada por leitura — é seguro justamente porque ninguém a altera. Não há `storageState`, não há sessão, não há login: o estado da aplicação vive na URL (spec §9), então cada teste monta a sua e chama `goto`. `retries` não mascara nada aqui, porque não existe estado sujo para uma segunda tentativa herdar.

**2. O que precisaria escrever, ganha um container.** O único cenário que exigiria mexer no ambiente compartilhado é o de API fora do ar (AC-18) — e `docker stop app` no meio de uma execução paralela derruba os outros workers junto. Em vez disso, o compose ganha um segundo serviço:

```yaml
web-sem-api:          # mesma imagem do web, API_BASE_URL apontando para um endereço morto
  environment:
    API_BASE_URL: http://127.0.0.1:9    # porta "discard", fecha na hora
```

O teste de resiliência é um projeto próprio do Playwright (`resiliencia`) com `baseURL` nessa instância. Roda em paralelo com todo o resto, não toca em nada de ninguém, e testa o caminho real de erro em vez de um mock. Custa um container ocioso de ~50 MB.

**3. Varreduras, no mesmo passo das outras.** `scripts/check-e2e-isolation.mjs` falha se encontrar em `tests/e2e/**`: `describe.serial`, `describe.configure({ mode: 'serial' })`, `let`/`var` no escopo de módulo, `beforeAll` com chamada de rede, `request.post|put|patch|delete`, ou a string `/admin/` (AC-31, AC-32). É análise textual, e é a primeira linha: o que ela não pega, a topologia invertida abaixo pega.

**Sobre embaralhar a ordem:** o Playwright não tem `--shuffle` nem equivalente — verificado na 1.62, no CLI e na tipagem. Não há como pedir ordem aleatória, então o substituto é rodar numa **topologia diferente** da padrão:

```
npm run e2e:isolamento   →  playwright test --workers=1 --repeat-each=2
```

- `--workers=1` põe todos os testes no mesmo processo, em sequência. Estado de módulo que vaza entre testes fica visível aqui; num run paralelo cada worker tem a sua cópia do módulo, e o vazamento pode passar despercebido.
- `--repeat-each=2` roda cada teste duas vezes seguidas no mesmo worker: pega o teste que suja o próprio ponto de partida.

Isso não é equivalente a embaralhar, e não vale fingir que é: um teste que depende de outro **na mesma ordem de arquivo** passa nas duas topologias. Contra esse caso a defesa é a varredura estática mais a revisão. Fica registrado como risco 10.

### DT-10.4 — Onde o AC-14 passou a viver

`ProductPublicationValidator` do 001 exige `product_content` nos dois idiomas para publicar. Nenhum produto publicado do seed tem tradução faltando, e criar um exigiria escrita via admin — proibida pela §7.2.

Então o aviso de tradução ausente é provado em **Vitest + Testing Library**, montando as props com os campos longos vazios. É o teste certo pelo motivo certo: a regra é de renderização, e um e2e para ela só provaria que o banco continua com os dados que o seed pôs lá.

O que o e2e continua cobrindo do AC-14: que a ficha de um produto **completo** renderiza os campos longos — o caminho que existe no seed.

### DT-10.2 — `data-testid`: convenção e como ela é cobrada

Convenção na spec §7.1. O que este plano acrescenta é **como isso deixa de depender de disciplina**:

- `lib/testid.ts` exporta os ids como constantes tipadas (`TESTID.produtoCard`), usadas tanto no componente quanto no teste. Id escrito à mão nos dois lados diverge no primeiro rename; constante compartilhada não.
- Um script (`scripts/check-testids.mjs`, rodando no job `web`) varre `src/components/**` e falha se um `button`, `a`, `input`, `select` ou `form` não tiver `data-testid` (AC-26); falha também se algum id casar com um slug ou nome do seed, ou com uma chave do catálogo de textos (AC-27).
- Um segundo script varre `tests/e2e/**` por `getByText(` fora de asserção e falha (AC-30). É grosseiro por opção: uma regra de ESLint dedicada custa mais do que resolve nesta fatia.

Ambos vivem no mesmo lugar da varredura de dado de produto hardcoded (AC-22), como um só passo de "regras que o compilador não pega".

### DT-11 — CORS

O caminho de renderização é servidor→servidor e não passa por CORS. Ainda assim, `next dev` fora do compose (`localhost:3000` batendo em `localhost:8080`) precisa que a API aceite a origem. Entra uma configuração de CORS no backend **restrita por variável de ambiente** (`CORS_ALLOWED_ORIGINS`, vazio por padrão) — em produção fica vazia, porque não há chamada de navegador para a API.

### DT-12 — CI

`ci.yml` ganha `paths` nos dois jobs: o job Java ignora `web/**`, e o job `web` (lint, typecheck, Vitest, build, **smoke e2e**) ignora `src/**` e `pom.xml`.

A smoke fica **dentro** do job `web`, e não num job à parte: ela precisa do build que o job acabou de fazer, e separar significaria construir a imagem duas vezes. O job sobe `docker compose up -d --wait` com o seed do 001, roda `npm run e2e:smoke`, e publica o relatório do Playwright como artefato só quando falha.

**A imagem do backend sai do job de teste, no mesmo commit** (decidido com Wesley em 2026-08-26, ao reestruturar o CI de uma vez só em vez de duas). O `Dockerfile` é multi-stage e compila do zero lá dentro (`mvn -DskipTests package`) — não consome nada do `./mvnw verify`, então a única coisa que o prende ao job de teste é estar no mesmo job. Medido em runs reais:

| Passo | run de documento | run com `src` | run de cache frio |
|---|---|---|---|
| `mvnw verify` | 93 s | 86 s | 100 s |
| build da imagem | 3 s | 5 s | **158 s** |

No cache frio a imagem custa mais que a suíte inteira, e hoje roda **depois** dela. Como job paralelo, o pior caso cai de ~4,5 min para ~2,8 min; o preço são ~35 s de overhead (runner, checkout, `setup-java`) nos runs de cache quente, e um runner gasto quando o teste falha. Vale.

O que **não** se separa: `build` de `test`. O job inteiro é o `mvnw verify` (86–100 s de ~110 s); um job "build" à parte duraria 5 s, jobs não compartilham disco, e passar o `target/` adiante custaria mais que o passo que se quer isolar. Separar surefire de failsafe fica para quando a suíte crescer — hoje o tempo é quase todo Testcontainers, e dividir duplicaria a compilação.

A full vive em `e2e-full.yml`, com `workflow_dispatch` (aceitando a branch como entrada). Não tem gatilho automático — é o que a exigência pede, e um agendamento noturno sem ninguém olhando o resultado é ruído, não cobertura. O README registra que rodar a full é passo obrigatório antes de promover para `main`.

Detalhe que morde: com `paths` em `push`, um check que não roda fica "pendente" em vez de verde. Hoje isso não trava nada — não há proteção de branch neste repositório (constituição §2) —, mas um check eternamente pendente é ruído que ensina a ignorar a coluna de status, e viraria trava no dia em que houver enforcement. Por isso todo job sempre roda e sempre reporta, com os passos condicionais. Isso vai numa tarefa própria, não misturado com código de página.

## 2. Estrutura de `web/`

```
web/
  app/
    layout.tsx                      # <html> sem lang — o lang vem do segmento
    [locale]/
      layout.tsx                    # valida locale, define <html lang>, header/footer
      page.tsx                      # home
      categorias/[slug]/page.tsx    # pt-BR
      categories/[slug]/page.tsx    # en
      produtos/[slug]/page.tsx      # pt-BR
      products/[slug]/page.tsx      # en
      buscar/page.tsx  · search/page.tsx
      comparar/page.tsx · compare/page.tsx
      not-found.tsx
      error.tsx
    sitemap.ts
    robots.ts
  middleware.ts                     # só o redirect de /
  src/
    components/
      ui/                           # shadcn (código copiado, versionado)
      catalog/                      # ProductCard, FiltroPainel, SeletorIdioma, TabelaComparacao
    lib/
      api/       client.ts · catalog.ts · types.gen.ts
      i18n/      locales.ts · messages.pt-BR.ts · messages.en.ts
      routes.ts · filters.ts · format.ts · testid.ts
      seo/       jsonld.ts · metadata.ts
  tests/
    unit/        (Vitest)
    e2e/         (Playwright — uma suíte; @smoke é recorte por tag)
  scripts/       check-testids.mjs · check-seed-leak.mjs · check-e2e-isolation.mjs
  Dockerfile
  next.config.ts · vitest.config.mts · playwright.config.ts
  # Tailwind 4 nao tem tailwind.config: a configuracao e CSS-first, em app/globals.css
  # vitest.config e .mts porque o pacote nao e ESM - .ts faria o Vite avisar a cada run
```

As duas rotas de cada par (`produtos`/`products`) são arquivos de três linhas que reexportam a mesma implementação com o locale fixo. Duplicar o caminho no `app/` é o preço do i18n por rota do App Router; duplicar a **lógica** seria erro.

### Fronteira com o backend

`web/` não conhece Java, `src/main/java` não conhece TypeScript. O único acoplamento é o documento OpenAPI, e ele atravessa via `types.gen.ts` gerado. Não existe código compartilhado entre os dois lados.

## 3. Textos de interface

`messages.pt-BR.ts` e `messages.en.ts`: objetos planos, mesma chave nos dois, com o tipo do `en` derivado do `pt-BR` (`type Messages = typeof ptBR`) — chave faltando em um idioma vira erro de compilação, não texto em branco em produção. Sem biblioteca de i18n: não há pluralização complexa nem formatação de mensagem nesta fatia, e `Intl` cobre número, moeda e data.

## 4. Rastreabilidade — critério de aceite → onde vive

| AC | Onde é implementado | Onde é verificado |
|---|---|---|
| AC-01 | `docker-compose.yml`, `web/Dockerfile` | manual no README |
| AC-02 | `.github/workflows/ci.yml` | inspeção da execução do CI |
| AC-03 | `middleware.ts` | e2e |
| AC-04 | `[locale]/layout.tsx`, `lib/i18n` | e2e |
| AC-05 | `SeletorIdioma` + `alternates` | e2e |
| AC-06 | `lib/seo/metadata.ts` | e2e + unidade |
| AC-07 | home | e2e |
| AC-08, AC-10 | `FiltroPainel`, `lib/filters` | e2e + unidade |
| AC-09 | `<form method="GET">` | e2e com JS off |
| AC-11 | lista, via `query.matched_by` | e2e |
| AC-12 | estado vazio da lista | e2e |
| AC-13 | `not-found.tsx` | e2e |
| AC-14 | ficha, campos longos vazios | **componente** (Vitest), ver DT-10.4 |
| AC-15 | `lib/seo/jsonld.ts` | unidade + e2e |
| AC-16, AC-17 | `comparar/page.tsx` | e2e |
| AC-18, AC-19 | `error.tsx`, `lib/api/client.ts` | e2e no projeto `resiliencia` (DT-10.3) + unidade |
| AC-20 | Radix + revisão | axe no Playwright |
| AC-21 | `app/sitemap.ts` | e2e |
| AC-22 | — (regra) | script de varredura no CI |
| AC-23 | `npm run api:types` | job do CI compara com o commitado |
| AC-24 | `server-only` em `lib/api` | e2e inspecionando o bundle |
| AC-25 | backend: `catalog` | teste de integração Java |
| AC-26, AC-27 | `lib/testid.ts` + componentes | `scripts/check-testids.mjs` no CI |
| AC-28 | `ci.yml`, job `web` | duração do job + `globalTimeout` |
| AC-29 | `.github/workflows/e2e-full.yml` | execução sob demanda |
| AC-30 | `tests/e2e/**` | varredura no CI |
| AC-31 | `playwright.config.ts` (`fullyParallel`) | `check-e2e-isolation.mjs` + `e2e:isolamento` |
| AC-32 | `tests/e2e/**` | mesma varredura |

## 5. Riscos e pontos de atenção

1. **O par de rotas traduzidas duplica arquivo.** Se a implementação duplicar lógica junto, a fatia apodrece rápido. A revisão de cada tarefa de página checa que o arquivo do par é só reexport.
2. **`server-only` protege o import, não o descuido.** Passar um objeto com `API_BASE_URL` como prop para um componente de cliente vaza pelo payload de hidratação. AC-24 tem que inspecionar o HTML servido, não só o bundle.
3. **Playwright com `javaScriptEnabled: false` não é o mesmo que "usuário sem JS"** — o HTML ainda é o do Next. É a melhor aproximação disponível e cobre o que importa: o `<form>` funcionar sem handler.
4. **`paths` no CI e o estado dos checks** (DT-12). Não há proteção de branch aqui (plano Free + repositório privado), então nada trava merge hoje — o risco real é o inverso: **é possível mergear vermelho sem que nada avise**. O desenho de "sempre roda, sempre reporta" mantém a coluna de status legível, que é a única defesa que sobra.
5. **Seed com 20 produtos não estressa paginação.** Os ACs de paginação usam `size` pequeno para chegar a mais de uma página com o seed real, em vez de inventar dados — que a constituição §7.5 proíbe.
6. **A smoke é o orçamento mais fácil de estourar.** Subir o compose no runner já custa 1–2 min antes do primeiro teste; se a smoke crescer para vinte casos, o feedback de push deixa de ser feedback. O `globalTimeout` de 5 min é proposital: ele quebra o build em vez de deixar o CI apodrecer devagar.
7. **O seed como fixture compartilhada só é seguro enquanto ninguém escreve.** A 003 (reviews) traz escrita, e no dia em que trouxer, a regra da §7.2 precisa virar "cada teste cria o próprio dado com id único" — não dá para estender o desenho atual por analogia.
8. **`data-testid` cobrado por varredura pega o esquecimento, não o id ruim.** O script garante que existe id; que ele seja estável e não venha do texto é revisão humana. Por isso a convenção está na spec, e não só no plano.
9. **O job `web` recompila o backend do zero para rodar a smoke.** `docker compose up --build` reconstrói a imagem Java sem cache de camada, duplicando o que o job `imagem` já faz com `cache-from: type=gha` — cerca de 110 s e uma dependência de rede ao Maven Central, que já derrubou o job uma vez. Mitigado por ora com retentativa no `mvn`. Quando voltar a doer, a saída é o job `imagem` publicar a imagem (artefato ou registry local) e o `web` consumi-la em vez de reconstruir; custa serializar os dois jobs, que hoje correm em paralelo.
10. **Não há como embaralhar a ordem dos testes no Playwright.** A topologia invertida (`--workers=1 --repeat-each=2`) cobre estado de módulo e teste que se suja, mas não cobre dependência que respeite a ordem dos arquivos. Se um dia isso morder, a saída é dividir a suíte com `--shard` em recortes diferentes, que muda quem roda junto com quem.
11. **`next/image` com domínios externos** pode falhar em ambiente sem saída para internet. O placeholder de fallback (DT-09) não é enfeite: é o que mantém o e2e verde offline.

## 6. Desvios propostos à spec

Nenhum. O único desvio desta fatia — o campo `alternates` — já foi aprovado e está incorporado à spec (§4.1), não a este plano.

## 7. Fora deste plano (confirmando a spec §2)

Interface de curadoria, reviews, ranking, assistente, conta de usuário, upload de imagem, PWA, CMS. E, especificamente: nenhum cache além do que o Next faz por padrão, nenhum banco no frontend, nenhum BFF.
