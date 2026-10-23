# Spec 002 — Frontend do catálogo (Next.js SSR bilíngue)

| Campo | Valor |
|---|---|
| Status | **Aprovada** (2026-08-26) — clarificações resolvidas; emendada no mesmo dia com as exigências de e2e desde o começo (§7.1) e independência entre testes (§7.2) |
| Módulos afetados | novo diretório `web/` (monorepo); no backend, o campo `alternates` em `catalog` e um ajuste de CI |
| Depende de | Spec 001 (API `/api/v1` publicada e documentada no OpenAPI), `CLAUDE.md` |
| Não inclui | Reviews, ranking, assistente, login de usuário final, painel de curadoria com interface |

## 1. Objetivo

Dar cara de produto ao que o 001 entregou: um site público, bilíngue e renderizado no servidor, onde alguém que nunca ouviu falar do projeto chega por uma busca, navega o catálogo por categoria, filtra, abre a ficha de um produto e compara alternativas — tudo em pt-BR ou en, com URL própria para cada idioma e HTML pronto no primeiro byte (o consumidor real dessas páginas, no começo, é o Google).

**Demo de sucesso:** `docker compose up --build` → abrir `http://localhost:3000` → a home lista as categorias com contagem de produtos → clicar em "Atendimento e chatbots" → filtrar por "tem plano gratuito" → abrir um produto → trocar para inglês no seletor e cair em `/en/products/<slug-en>` com o mesmo produto em inglês. Com JavaScript desabilitado no navegador, navegar e filtrar continua funcionando.

## 2. Fora de escopo (explícito)

- Interface de curadoria (admin continua sendo Swagger + HTTP Basic; a spec de identidade é a 006).
- Reviews, notas, "em alta", qualquer ranking além da ordenação que a API já devolve.
- Chat / assistente.
- Conta de usuário, favoritos, listas salvas — nada que precise de sessão autenticada.
- Upload de imagem: o logo continua sendo URL externa.
- App mobile, PWA, service worker.
- CMS para as páginas institucionais.

## 3. Usuários e cenários

**Visitante que chega pelo Google** (cenário dominante): cai direto numa ficha de produto ou numa página de categoria, sem passar pela home. A página precisa fazer sentido isolada, no idioma da URL, e carregar rápido em 4G.

**Visitante que explora:** home → categoria → filtros → ficha → comparação. Muda de ideia, volta, refaz o filtro. O estado da navegação vive na URL, não em memória do cliente — voltar no navegador tem que devolver a mesma lista.

**Curador (Wesley):** não usa este frontend para trabalhar; usa para conferir como o conteúdo curado aparece publicado, principalmente se um produto ficou sem tradução.

## 4. Rotas e idioma

i18n por rota, com prefixo obrigatório e explícito nos dois idiomas — sem idioma "sem prefixo", para não existir duas URLs servindo a mesma coisa:

| Rota | Página |
|---|---|
| `/pt-BR` · `/en` | Home: categorias raiz com contagem, busca, destaque do catálogo |
| `/pt-BR/categorias/{slug}` · `/en/categories/{slug}` | Lista de produtos da categoria (inclui descendentes), com filtros |
| `/pt-BR/buscar?q=` · `/en/search?q=` | Mesma lista, originada de busca textual |
| `/pt-BR/produtos/{slug}` · `/en/products/{slug}` | Ficha completa |
| `/pt-BR/comparar?slugs=a,b` · `/en/compare?slugs=a,b` | Comparação de 2 a 3 produtos |

Regras:

- **`/` (raiz sem prefixo) redireciona** conforme `Accept-Language` do navegador, com `307`, para `/pt-BR` ou `/en`. Default `pt-BR`, igual à API.
- O prefixo da URL é a **única** fonte do idioma. Cookie não muda o conteúdo de uma URL já prefixada — no máximo alimenta o redirect da raiz.
- Os segmentos de rota são traduzidos (`categorias`/`categories`, `produtos`/`products`). O **slug de categoria** também muda com o idioma (`slug_pt`/`slug_en`); o **slug de produto não**, porque nome de produto não se traduz (spec 001 §4.2). Então `/pt-BR/produtos/zapier` e `/en/products/zapier` são a mesma ficha, e `/pt-BR/categorias/atendimento-e-chatbots` corresponde a `/en/categories/customer-support-and-chatbots`.
- **Seletor de idioma preserva a página:** estando em `/pt-BR/produtos/notion`, trocar para inglês vai para `/en/products/notion` (o slug en daquele produto), não para a home. Se a página atual não existir no outro idioma, cai na home daquele idioma.
- Toda página emite `<link rel="alternate" hreflang>` para as duas versões e `x-default` apontando para pt-BR.

### 4.1 Desvio aprovado sobre a spec 001 — campo `alternates`

Para montar a URL alternativa e o `hreflang`, o frontend precisa saber o slug da página no outro idioma. Onde o slug **não** muda — produto — ele já tem a informação: é o mesmo slug, com o segmento de rota traduzido localmente. Onde muda — categoria — a API não devolve o par, e descobri-lo custaria uma segunda chamada em toda página rastreada.

**Decidido (2026-08-26, Wesley) e corrigido em escopo (2026-08-27):** `GET /categories/{slug}` passa a devolver:

```json
"alternates": { "pt-BR": "atendimento-e-chatbots", "en": "customer-support-and-chatbots" }
```

- **Só nessa rota.** Não entra em `GET /categories` (árvore) nem em nada de produto. A versão anterior desta seção mandava o campo também para `GET /products/{slug}` e `GET /products/compare`, apoiada numa afirmação errada minha de que produto teria `slug_pt`/`slug_en`. Produto tem um slug só (spec 001 §4.2), então lá o campo seria a mesma string repetida duas vezes — ruído no contrato, não informação.
- **Sem migração:** `slug_pt` e `slug_en` já existem na tabela `category`. É projeção nova, não coluna nova.
- Este é um desvio sobre uma spec fechada: a tarefa que implementar o campo **também atualiza `specs/001-catalogo-core/spec.md` no mesmo commit** (constituição §2.5 — a spec é a fonte da verdade, e ela vem antes do código).

## 5. Páginas

### 5.1 Home

Consome `GET /categories`. Mostra as categorias raiz com nome, descrição curta e `product_count`, as subcategorias como links secundários, e um campo de busca que submete para a rota de busca. Sem carrossel, sem "destaques" — não há critério de destaque nesta fatia e inventar um violaria a constituição §7.

### 5.2 Categoria e busca (mesma lista, duas portas de entrada)

Consome `GET /products` com `category` ou `q`, mais os filtros. Cada filtro é um parâmetro na URL, e a página é renderizada no servidor a partir dela:

| Controle | Parâmetro da API |
|---|---|
| Integração (multi) | `integration` |
| Modelo de cobrança (multi) | `pricing_model` |
| Tem teste grátis | `has_free_trial` |
| Tem plano gratuito | `has_free_plan` |
| Preço até | `price_max` |
| Página | `page`, `size` |

- Os filtros são um `<form method="GET">`: **funcionam sem JavaScript**. Com JS, submetem sozinhos ao mudar e atualizam a URL sem recarregar tudo.
- A lista de integrações do filtro vem de `GET /integrations` (que já traz a contagem por integração).
- Quando a busca caiu no fallback de trigram (`query.matched_by = "TRIGRAM"`), a página avisa: "Nada exato para *X* — mostrando o que mais se aproxima."
- Zero resultados não é erro: mostra o que foi filtrado, um botão para limpar cada filtro e as categorias como saída.
- Paginação é `?page=`, com links reais `<a>` (rastreáveis), `rel="prev"/"next"`.
- Cada card tem um controle "comparar" que acumula slugs na URL de comparação (máximo 3, o limite da API).

### 5.3 Ficha do produto

Consome `GET /products/{slug}`. Renderiza `name`, `tagline`, `description`, logo, `pros`, `cons`, `best_for`, `faq`, preço (`price_min` + `price_currency` + `price_period`, ou "sob consulta" quando não houver), `pricing_model`, badges de teste/plano grátis, `supported_languages`, `hq_country`, integrações, tags e a categoria com link.

- Link externo para o site do produto com `rel="nofollow sponsored noopener"` e `target="_blank"`.
- `updated_at` visível como "atualizado em" — transparência é posicionamento do produto.
- **Produto sem conteúdo curado no idioma:** a API responde 200 com os campos longos vazios (spec 001 §5.1). A página não pode ficar quebrada: mostra o que existe e um aviso "conteúdo ainda não disponível em português", com link para a versão que tem conteúdo.
- 404 da API vira **404 de verdade** (status HTTP 404 + página de não encontrado), nunca uma página vazia com 200.

### 5.4 Comparação

Consome `GET /products/compare?slugs=`. Tabela com os produtos em colunas e os atributos em linhas, primeira coluna fixa ao rolar horizontalmente no mobile. Menos de 2 ou mais de 3 slugs → a própria página trata (a API devolveria 400): mensagem pedindo escolher de 2 a 3. Slug inexistente → a API devolve 404 com `missing_slugs`; a página mostra quais sumiram e oferece comparar os que restaram.

## 6. Contrato com a API

- O frontend **só** consome `/api/v1/**` público. Nunca fala com o banco, nunca usa as rotas `/admin/**`.
- A base da API vem de variável de ambiente (`API_BASE_URL` no servidor). Nenhuma URL de API hardcoded no bundle do cliente.
- As chamadas de renderização acontecem **no servidor** (server components / SSR): o navegador do visitante não chama a API diretamente na primeira carga. Isso mantém a API atrás do compose e evita CORS no caminho crítico.
- Os tipos de request/response são **gerados a partir do `/v3/api-docs`**, não escritos à mão — o OpenAPI do 001 é o contrato, e um `npm run api:types` versionado regenera. Divergência entre o tipo gerado e o commitado quebra o build.
- `Accept-Language` é enviado em toda chamada a partir do prefixo da rota; `?lang=` não é usado (um mecanismo só).
- Erro da API em `application/problem+json` vira uma página de erro no idioma da rota. A API **já responde o erro no idioma da requisição** (`ProblemDetailFactory` resolve as mensagens por `RequestLocale`, com `messages_en.properties`), então `detail` pode ser exibido — mas quem decide **qual página** é o par `type` + `status`, nunca o texto. Mensagem de erro de sistema é do frontend; `detail` só aparece quando acrescenta informação ao visitante (ex.: qual parâmetro é inválido).

## 7. Qualidade não-funcional

- **Sem JavaScript, o site funciona:** navegar, filtrar, paginar e comparar. JS melhora (filtro instantâneo, seleção de comparação), não habilita.
- **Acessibilidade:** navegação por teclado em todos os controles, foco visível, contraste AA, `<html lang>` correto por rota, um `<h1>` por página, imagens com `alt`, filtros como `fieldset`/`legend`.
- **SEO:** `title` e `meta description` por página derivados do conteúdo, canônica absoluta, `hreflang` recíproco, JSON-LD `SoftwareApplication` na ficha e `BreadcrumbList` na categoria, `sitemap.xml` com as duas versões de cada URL e `robots.txt`.
- **Performance:** orçamento de LCP ≤ 2,5 s e CLS ≤ 0,1 em 4G simulada na ficha de produto; logos com `width`/`height` declarados para não deslocar o layout.
- **Estados:** toda página tem estado de carregamento, de vazio e de erro desenhados. Nenhum spinner infinito quando a API estiver fora.
- **Nada de dado de produto no frontend:** nenhum nome, preço ou descrição hardcoded, nem em mock de teste que vá para produção (constituição §7).

### 7.1 Teste de ponta a ponta desde a primeira tarefa (exigência, 2026-08-26)

Não existe "fase de testes" no fim desta fatia. O Playwright entra na **Fase 0**, contra o `docker compose` real, antes de existir a primeira página — e cada tarefa de tela entrega o teste junto com a tela, no mesmo commit.

**`data-testid` é obrigatório.** Todo elemento que um teste precise alcançar — controle, card, região de conteúdo, estado vazio, aviso — carrega `data-testid`. A razão é específica deste produto: a interface existe em dois idiomas, então seletor por texto visível quebra ao trocar de idioma, e seletor por classe do Tailwind quebra ao mexer no estilo. `data-testid` é o único ponto de ancoragem que sobrevive aos dois.

Convenção, para o id não virar loteria:

- kebab-case, do escopo para o detalhe: `produto-card`, `produto-card-preco`, `filtro-plano-gratuito`, `seletor-idioma`, `paginacao-proxima`, `lista-vazia`.
- **Nunca derivado de texto traduzível** nem do conteúdo do seed. Item de lista usa um id de tipo (`produto-card`) mais um atributo que identifica qual (`data-slug`) — id único por produto amarraria o teste ao seed.
- Em português, igual ao resto do código do projeto.
- Nos testes, `getByTestId`. `getByText` só é permitido quando a asserção **é sobre o texto** (conferir que a página está em inglês, por exemplo).

**Duas suítes, dois gatilhos:**

| Suíte | Quando roda | O que cobre | Orçamento |
|---|---|---|---|
| **Smoke** (`@smoke`) | Todo push, no CI, junto com lint e build | O caminho crítico: home responde, categoria lista, ficha abre, idioma troca, slug inexistente dá 404 | ≤ 5 min, só Chromium |
| **Full** | Ao promover para `homol` (e por `workflow_dispatch`, quando disponível) | Todos os ACs de fluxo, o projeto com JavaScript desabilitado, axe, viewport mobile | Sem limite de tempo |

Todo teste da smoke também está na full — smoke é um recorte por tag, não uma suíte paralela a manter.

### 7.2 Independência entre testes (exigência, 2026-08-26)

A suíte roda **em paralelo**, então cada teste é independente: ordem arbitrária, worker arbitrário, nenhum estado ou dado compartilhado com outro teste. Um teste que só passa depois de outro é um teste quebrado que ainda não falhou.

**A suíte e2e não escreve.** Nenhum teste chama `/api/v1/admin/**`, nem faz requisição que não seja `GET` contra a API. Isso não é uma restrição inventada: o frontend desta fatia é somente leitura, então a suíte que o exercita também é. O seed do 001 é a fixture — compartilhada, mas **imutável**, o que a torna segura para N workers lendo ao mesmo tempo.

Consequências práticas:

- Proibidos: `test.describe.serial`, `storageState` compartilhado entre testes, variável de módulo mutável, `beforeAll` que altera estado, e teste que depende da navegação deixada por outro. Cada teste começa no seu próprio `goto`.
- Estado da aplicação vive na URL (§9), então cada teste monta a URL de que precisa — não há sessão a preparar.
- **Cenário que exige backend diferente não altera o compartilhado: sobe outra instância.** É o caso do teste de API fora do ar (AC-18): em vez de derrubar o container que os outros workers estão usando, o teste aponta para uma segunda instância do `web` configurada contra um endereço morto.
- Quando uma spec futura tiver escrita (a 003, de reviews), a regra vira "cada teste cria o próprio dado com identificador único e não o reaproveita". Fora do escopo desta fatia, registrado para não ser redescoberto.

### 7.3 Consequência sobre o AC de tradução ausente

`ProductPublicationValidator` do 001 exige `product_content` nos dois idiomas para publicar, então **nenhum produto publicado tem tradução faltando** — o estado que o AC-14 descreve é inalcançável por leitura do seed, e alcançá-lo exigiria escrita via admin, que a §7.2 proíbe.

A regra continua valendo: a ficha não pode quebrar quando os campos longos vierem vazios (a API documenta essa resposta). O que muda é **onde se prova**: teste de componente com as props montadas à mão, não e2e contra o banco. É rendering, não integração.

## 8. Infra e execução

- Diretório `web/` no mesmo repositório. `docker-compose.yml` da raiz ganha o serviço `web`, dependente de `app`, exposto em `WEB_PORT` (default `3000`).
- `docker compose up --build` continua sendo o único comando: db + api + web.
- CI ganha filtro por path — `mvn verify` não roda quando só `web/` muda, e o job do frontend (lint + typecheck + unidade + build + **smoke e2e**) não roda quando só o Java muda.
- A smoke sobe o compose inteiro no CI: sem API real não há e2e, pelo mesmo motivo que a constituição §7.3 proíbe H2 nos testes do backend.
- `CLAUDE.md` ganha a seção de frontend (§3 hoje diz "não faz parte das specs iniciais" e §4 só descreve o monolito Java). A atualização da constituição é parte desta spec, não um "já que estou aqui".

### 8.1 Stack do `web/` (decidida em 2026-08-26 com Wesley)

| Camada | Escolha | Por quê |
|---|---|---|
| Framework | Next.js (App Router, React Server Components) | Constituição §3; primeira renderização é HTML |
| Linguagem | TypeScript estrito | Os tipos da API são gerados; `any` no contrato anula o ganho |
| Estilo | Tailwind | Sem CSS órfão, sem cascata para depurar |
| Componentes | shadcn/ui sobre Radix | Código copiado para o repositório, não dependência opaca — casa com a constituição §7.1. Radix dá teclado e ARIA de fábrica, que é metade dos ACs de acessibilidade |
| Testes de unidade | Vitest + Testing Library | Regras puras: montagem de query string, resolução de idioma, formatação de preço |
| Testes de fluxo | Playwright | Os ACs de navegação, incluindo os de JavaScript desabilitado, que só existem num navegador real |
| Tipos da API | `openapi-typescript` contra `/v3/api-docs` | O OpenAPI do 001 é o contrato |

Toda dependência acima está justificada aqui; qualquer outra exige entrada na tabela do `plan.md` (constituição §7.1).

### 8.2 Produção — container próprio (Coolify)

**Decidido:** o `Dockerfile` do `web/` é o artefato de produção, com `output: "standalone"` no Next.js e build multi-stage rodando como não-root, igual ao Dockerfile do backend. Não há adapter de plataforma, nem função serverless, nem otimização de imagem dependente de fornecedor — o que roda no `docker compose` local é o mesmo que roda no Coolify. Consequências que o `plan.md` precisa tratar: cache de imagem remota é responsabilidade do próprio Next (`next/image` com loader padrão e `remotePatterns` para os domínios dos logos), e `API_BASE_URL` aponta para o serviço interno, nunca para um host público.

## 9. Decisões já tomadas (não reabrir nesta spec)

- **Monorepo** com o frontend em `web/`: uma constituição, um `specs/`, um compose, e mudança de contrato entrando num commit só. Separar depois é `git filter-repo`, não uma porta de mão única.
- **Next.js com App Router e SSR**, conforme a constituição §3. Não é SPA: a primeira renderização é HTML, porque o consumidor principal é buscador.
- **i18n por rota com prefixo em ambos os idiomas** — nenhum idioma sem prefixo, para não haver URL duplicada.
- **O estado da navegação vive na URL.** Filtro, página e comparação são compartilháveis e sobrevivem ao botão "voltar".
- **Sem gerenciador de estado global** (Redux/Zustand): não há estado de cliente que justifique.
- **Sem banco, sem cache distribuído e sem BFF próprio** no frontend: ele fala com a API do 001 e ponto.
- **E2E desde a primeira tarefa, com `data-testid` obrigatório, smoke no CI e full sob demanda** (§7.1). Vira regra de constituição em T00, não convenção desta spec.
- **Testes independentes e paralelos, e suíte e2e somente leitura** (§7.2). O seed é fixture imutável; cenário que precise de outro backend ganha outra instância, não uma mutação.

## 10. Critérios de aceite

Cada item vira ao menos um teste automatizado (Playwright para fluxo, unidade para regra), salvo indicação de "manual".

**Infra**
- [ ] AC-01 `docker compose up --build` de um clone limpo sobe db + api + web; `http://localhost:3000` responde 200 com HTML. (manual, documentado no README)
- [ ] AC-02 `mvn verify` não é executado no CI num commit que só toca `web/`.

**Idioma e rotas**
- [ ] AC-03 `GET /` com `Accept-Language: en` redireciona 307 para `/en`; sem header, para `/pt-BR`.
- [ ] AC-04 `/en/products/{slug-en}` responde com `<html lang="en">` e nenhum texto de interface em português.
- [ ] AC-05 Trocar o idioma na ficha leva ao mesmo produto no outro idioma, não à home.
- [ ] AC-06 Toda página renderizada traz `hreflang` recíproco para as duas versões e uma canônica absoluta.

**Catálogo**
- [ ] AC-07 A home lista as categorias raiz com a mesma `product_count` que a API devolve.
- [ ] AC-08 Filtrar por `has_free_plan=true` muda a URL e o conjunto retornado; recarregar a URL reproduz a mesma lista.
- [ ] AC-09 Com JavaScript desabilitado, aplicar um filtro e paginar continuam funcionando. (Playwright com JS off)
- [ ] AC-10 Voltar no navegador depois de filtrar devolve a lista anterior, não a lista sem filtro.
- [ ] AC-11 Busca com erro de digitação exibe o aviso de resultado aproximado quando `matched_by = "TRIGRAM"`.
- [ ] AC-12 Zero resultados mostra a página de vazio com os filtros aplicados e opção de limpar, com status 200.

**Ficha e comparação**
- [ ] AC-13 Slug inexistente responde **HTTP 404** e a página de não encontrado no idioma da rota.
- [ ] AC-14 Ficha com os campos longos vazios renderiza sem quebrar, com o aviso de tradução ausente. **Teste de componente**, não e2e — o estado é inalcançável pelo seed sem escrita (§7.3).
- [ ] AC-15 A ficha traz JSON-LD `SoftwareApplication` válido com nome, categoria e preço quando houver.
- [ ] AC-16 Comparar 3 produtos mostra as 3 colunas na ordem pedida; 1 slug mostra o aviso de faixa, sem erro 500.
- [ ] AC-17 Slug inexistente na comparação mostra quais faltaram, a partir de `missing_slugs`.

**Qualidade**
- [ ] AC-18 API fora do ar durante a renderização produz página de erro no idioma correto, com status 5xx e sem stack trace na resposta.
- [ ] AC-19 Nenhum `detail` cru de Problem Details aparece em página em inglês.
- [ ] AC-20 Axe não acusa violação crítica ou séria na home, na categoria e na ficha.
- [ ] AC-21 `sitemap.xml` lista as duas versões de idioma de cada categoria e produto publicado.
- [ ] AC-22 Nenhum nome, preço ou descrição de produto aparece hardcoded em `web/` (varredura contra os slugs do seed).
- [ ] AC-23 Os tipos da API são gerados do `/v3/api-docs`; regenerar não produz diferença contra o que está commitado.
- [ ] AC-24 O bundle enviado ao cliente não contém `API_BASE_URL` interno nem credencial.

**Teste e testabilidade**
- [ ] AC-26 Nenhum componente de `web/src/components/` tem elemento interativo (`button`, `a`, `input`, `select`, `form`) sem `data-testid`; varredura no CI falha se tiver.
- [ ] AC-27 Nenhum `data-testid` do repositório contém texto traduzível ou slug do seed.
- [ ] AC-28 A smoke roda em todo push, sobe o compose, e fecha em ≤ 5 min.
- [ ] AC-29 A full roda ao promover para `homol` e cobre todos os ACs de fluxo desta spec; o `workflow_dispatch` continua disponível como disparo avulso.
- [ ] AC-30 Nenhum teste e2e usa `getByText` para alcançar elemento — só para afirmar sobre texto. (varredura)
- [ ] AC-31 A suíte roda com `fullyParallel` e mais de um worker, **e passa também na topologia invertida** (`--workers=1 --repeat-each=2`). Nenhum `describe.serial` nem estado de módulo compartilhado. (varredura + segunda execução)
- [ ] AC-32 Nenhum teste e2e chama `/api/v1/admin/**` nem emite requisição não-`GET` contra a API. (varredura)

**Desvio sobre o 001**
- [ ] AC-25 `GET /categories/{slug}` devolve `alternates` com os dois slugs, e `GET /categories` (árvore) **não** ganha o campo; o OpenAPI e a `spec.md` do 001 refletem a mudança, e nenhuma migração Flyway foi criada para isso.

## 11. Perguntas resolvidas

| # | Pergunta | Resolução | Onde |
|---|---|---|---|
| 1 | Como o frontend descobre o slug do outro idioma? | Desvio no 001: campo `alternates` nas rotas de item. Sem migração; a tarefa atualiza a spec 001 no mesmo commit | §4.1 |
| 2 | Os erros da API vêm num idioma só? | Não — já são localizados por `Accept-Language` (`ProblemDetailFactory` + `messages_en.properties`). Resolvida na redação, lendo o código do 001 | §6 |
| 3 | Estilo, componentes e testes | Tailwind + shadcn/ui sobre Radix; Vitest para unidade, Playwright para fluxo | §8.1 |
| 4 | Hospedagem | Container próprio (Coolify), `output: standalone`. Sem adapter de fornecedor | §8.2 |

## 12. Próximas specs (para contexto, não implementar)

- **003-reviews** — reviews, verificação por domínio, moderação.
- **004-ranking-v1** — snapshot de score por categoria, página "como ranqueamos".
- **005-assistente-rag** — ingestão de `product_content` em `knowledge_chunk` com pgvector, chat com streaming.
- **006-identidade** — substitui HTTP Basic; usuários, magic link, domínio corporativo.
