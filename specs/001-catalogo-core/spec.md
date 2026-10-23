# Spec 001 — Catálogo core (API bilíngue rodando em Docker)

| Campo | Valor |
|---|---|
| Status | **Aprovada** (2026-08-26) — clarificações resolvidas e desvios D-01..D-07 do `plan.md` incorporados |
| Módulos afetados | `catalog`, `search`, `admin`, `shared` |
| Depende de | `CONSTITUTION.md` |
| Não inclui | Frontend, reviews, ranking, assistente, autenticação de usuário final |

## 1. Objetivo

Ter, ao final desta fatia, algo que roda com um comando e já mostra a essência do produto: um catálogo de produtos SaaS com conteúdo em português e inglês, navegável por categoria, pesquisável em qualquer um dos dois idiomas, com um conjunto real de dados de seed e uma API documentada que o frontend (spec futura) e o assistente (spec futura) vão consumir.

**Demo de sucesso:** `docker compose up --build` → abrir `http://localhost:8080/swagger-ui.html` → chamar `GET /api/v1/products?q=atendimento whatsapp&lang=pt-BR` e receber uma lista relevante dos produtos do seed; repetir com `?q=customer support&lang=en` e receber os mesmos produtos com conteúdo em inglês.

## 2. Fora de escopo (explícito)

- Qualquer interface web além do Swagger UI.
- Reviews, notas, ranking, "em alta".
- Assistente / embeddings / pgvector (a extensão é habilitada na migração inicial, mas nenhuma tabela a usa ainda).
- Cadastro/login de usuários. Endpoints de escrita usam **HTTP Basic** com credenciais de ambiente (`ADMIN_USER`, `ADMIN_PASSWORD`) — provisório, só para curadoria.
- Upload de imagens (logo é uma URL externa por enquanto).
- Vendedor (`vendor`) como entidade — fica para a spec de claim de listagem.
- Paginação por cursor (offset é suficiente).

## 3. Usuários e cenários

**Curador (você):** cadastra e mantém categorias e produtos via API, em pt e en. Precisa listar rascunhos, publicar e despublicar.

**Consumidor da API (frontend/assistente futuros, ou alguém testando no Swagger):** lista categorias, navega produtos por categoria, busca por texto no idioma que preferir, abre a ficha completa de um produto, compara até 3 produtos.

## 4. Modelo de domínio

### 4.1 `category`
| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| slug_pt, slug_en | text | únicos entre si (índice único em cada), kebab-case, imutáveis após criação |
| name_pt, name_en | text | obrigatórios, ≤ 80 chars |
| description_pt, description_en | text | opcionais, ≤ 500 chars |
| parent_id | uuid | FK opcional para `category`; profundidade máxima 2 (categoria → subcategoria) |
| sort_order | int | default 0 |
| created_at, updated_at | timestamptz | |

### 4.1.1 `category_slug` (lookup de slug bilíngue)

Garante unicidade **global** de slug entre os dois idiomas: sem isso nada impede o `slug_pt` de uma categoria colidir com o `slug_en` de outra, e `GET /categories/{slug}` (AC-04) fica ambíguo. Serve também para resolver a rota numa query só.

| Campo | Tipo | Regras |
|---|---|---|
| slug | text | PK — único entre **todas** as categorias e **ambos** os idiomas |
| category_id | uuid | FK para `category` |
| locale | text | `pt-BR` ou `en` |

Mantida por trigger em `category` (INSERT/UPDATE). Não é exposta na API.

### 4.2 `product`
| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| slug | text | único, imutável após publicação; igual nos dois idiomas (nome de produto não se traduz) |
| name | text | obrigatório, ≤ 120 |
| tagline_pt, tagline_en | text | obrigatórios, ≤ 160 |
| description_pt, description_en | text | obrigatórios, ≤ 4000 |
| category_id | uuid | FK obrigatória |
| website_url | text | obrigatório, URL válida |
| logo_url | text | opcional, URL |
| pricing_model | enum | `FREE`, `FREEMIUM`, `SUBSCRIPTION`, `USAGE_BASED`, `ONE_TIME`, `CONTACT_SALES` |
| price_min | numeric(12,4) | opcional; obrigatório se pricing_model ∈ {SUBSCRIPTION, USAGE_BASED, ONE_TIME}. Essa obrigatoriedade é verificada **na publicação** (422), não como constraint de banco — o curador precisa poder salvar rascunho incompleto. **Corrigido no T26:** eram 2 casas, e produto cobrado por unidade (US$ 0,005 por mensagem) era arredondado para o dobro. Quatro casas cobrem o milésimo de dólar que os provedores praticam |
| price_currency | char(3) | ISO 4217; obrigatório se price_min informado |
| price_period | enum | `MONTH`, `YEAR`, `ONE_TIME`, opcional |
| has_free_trial | bool | default false |
| has_free_plan | bool | default false |
| supported_languages | text[] | códigos ISO 639-1; ao menos 1 |
| hq_country | char(2) | ISO 3166-1 alpha-2, opcional |
| status | enum | `DRAFT`, `PUBLISHED`, `ARCHIVED`; default DRAFT |
| published_at | timestamptz | setado na primeira publicação |
| source_note | text | de onde veio o dado (URL da pricing page, data da coleta). Obrigatório. Não exposto publicamente. |
| created_at, updated_at | timestamptz | |

### 4.3 `product_content` (conteúdo curado longo, um registro por idioma)
| Campo | Tipo | Regras |
|---|---|---|
| product_id + locale | PK composta | locale ∈ {pt-BR, en} |
| pros | text[] | 0–8 itens, cada ≤ 200 |
| cons | text[] | 0–8 itens, cada ≤ 200 |
| best_for | text | ≤ 300 |
| faq | jsonb | array de `{question, answer}`, 0–10 itens |

### 4.4 `integration` e `product_integration`
- `integration(id, slug único, name)` — ex.: `whatsapp`, `hubspot`, `shopify`, `slack`, `zapier`.
- `product_integration(product_id, integration_id)` — N:N.

### 4.5 `tag` e `product_tag`
- `tag(id, slug único, name_pt, name_en)`.
- `product_tag(product_id, tag_id)`.

### 4.6 Busca
- Colunas `search_vector_pt tsvector` e `search_vector_en tsvector` em `product`, materializadas por **trigger** — não por coluna gerada: o vetor inclui nomes de tags e integrações e `GENERATED ALWAYS` só enxerga a própria linha. Composição: name (peso A), tagline (B), description (C), nomes de tags e integrações (D). Usar `unaccent` + dicionário `portuguese` / `english`.
- Triggers de recálculo em `product`, `product_tag`, `product_integration`, `tag` e `integration`, para que qualquer edição reflita na busca sem passo de aplicação (AC-22).
- A consulta do usuário passa por `websearch_to_tsquery`, que tolera aspas, `or` e `-` e não quebra com entrada livre.
- Índices GIN nos dois vetores.
- Índice `pg_trgm` em `name` para tolerância a erro de digitação no nome do produto. **Precisado no T08:** o índice é criado sobre `lower(f_unaccent(name))`, a mesma forma que a consulta do fallback compara — sobre o `name` cru o índice não seria usado. `f_unaccent` é um wrapper `IMMUTABLE` com o dicionário fixado, porque `unaccent` é `STABLE` e não pode ser indexada.
- **Precisado no T08:** no peso D, cada vetor recebe os termos do seu idioma — `tag.name_pt` no vetor pt, `tag.name_en` no vetor en. Misturar os dois faria a busca em português casar com o nome em inglês da tag, poluindo o ranking do idioma pedido. Nome de integração não se traduz e entra nos dois.

## 5. API

Base: `/api/v1`. Corpo, query params e campos de resposta em `snake_case`. Idioma via header `Accept-Language` ou query `lang` (query vence). Valores aceitos: `pt-BR`, `en`. `lang` explícito fora dessa lista → 400; `Accept-Language` contendo apenas idiomas não suportados cai no default (`APP_DEFAULT_LOCALE`, `pt-BR`) sem erro, como manda a negociação de conteúdo HTTP. Toda resposta inclui header `Content-Language`.

Resposta de produto **resolvida por idioma**: o cliente recebe `tagline`, `description`, `pros`, `cons`, `best_for`, `faq` já no idioma pedido, mais `locale`. Não recebe os dois idiomas ao mesmo tempo, exceto em endpoints de admin.

### 5.1 Público (sem auth)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/categories` | Árvore de categorias (raiz com filhas), com contagem de produtos publicados em cada uma. `product_count` **inclui as subcategorias**, para bater com o comportamento do filtro `category=`. |
| GET | `/categories/{slug}` | Uma categoria; aceita slug em qualquer idioma. Responde também `alternates` com o slug nos dois idiomas. |
| GET | `/products` | Lista/busca de produtos **publicados**. Query params abaixo. |
| GET | `/products/{slug}` | Ficha completa de um produto publicado. 404 se draft/archived. |
| GET | `/products/compare?slugs=a,b,c` | 2 a 3 produtos lado a lado (mesma estrutura da ficha, em array). 400 se < 2 ou > 3. |
| GET | `/integrations` | Lista de integrações com contagem de produtos. |

Parâmetros de `GET /products`:

| Param | Tipo | Comportamento |
|---|---|---|
| q | string | Busca full-text no vetor do idioma pedido via `websearch_to_tsquery(unaccent(q))`. Se vazio, lista por `updated_at desc`. Ordenação por `ts_rank_cd` quando `q` presente. Fallback: se 0 resultados no FTS, tentar similaridade trigram no `name` (threshold 0.3). |
| category | slug | Inclui subcategorias. |
| integration | slug, repetível | AND entre valores. |
| pricing_model | enum, repetível | OR entre valores. |
| has_free_trial, has_free_plan | bool | |
| price_max | número | Considera `price_min <= price_max`; ignora produtos sem preço. |
| page, size | int | default 0 / 20; size máx 50. |

Resposta: `{ locale, items: [...], page, size, total, query: { echo dos filtros aplicados, matched_by } }`. `matched_by` ∈ `FULL_TEXT` | `TRIGRAM` | `NONE` torna o fallback trigram observável pela API, em vez de verificável só por inspeção de banco. **Precisado no T16:** sem `q` na requisição, `matched_by` sai `null` — não houve casamento a relatar, e `NONE` significaria "nada casou", que é diferente.
Item de lista é um resumo: `slug, name, tagline, logo_url, category {slug, name}, pricing_model, price_min, price_currency, price_period, has_free_trial, has_free_plan, integrations[]`.

### 5.2 Admin (HTTP Basic)

| Método | Rota | Descrição |
|---|---|---|
| GET/POST | `/admin/categories`, `PUT /admin/categories/{id}` | CRUD. DELETE só se sem produtos e sem filhas (409 caso contrário). |
| GET | `/admin/products?status=` | Lista todos os status. |
| POST | `/admin/products` | Cria em DRAFT. Corpo contém **ambos os idiomas**. |
| PUT | `/admin/products/{id}` | Atualiza. Slug não pode mudar se PUBLISHED (409). |
| PUT | `/admin/products/{id}/content/{locale}` | Substitui pros/cons/best_for/faq daquele idioma. |
| POST | `/admin/products/{id}/publish` | DRAFT → PUBLISHED. Valida completude: ambos idiomas preenchidos, ≥ 1 idioma suportado, preço coerente com pricing_model. Erro 422 lista o que falta. |
| POST | `/admin/products/{id}/unpublish` | PUBLISHED → DRAFT. |
| POST | `/admin/products/{id}/archive` | Qualquer → ARCHIVED. |
| GET/POST | `/admin/integrations`, `/admin/tags` | CRUD simples. |

### 5.3 Operacional
- `GET /actuator/health` público. Inclui check do Postgres.
- `GET /swagger-ui.html` e `/v3/api-docs` públicos.

## 6. Seed

- Arquivo `src/main/resources/seed/` em YAML ou JSON, versionado, aplicado por um `CommandLineRunner` **somente** quando `SEED_ENABLED=true` e o banco estiver vazio (idempotente).
- Conteúdo mínimo: 1 categoria raiz **"IA e automação para PMEs"** com 4 subcategorias (`atendimento-e-chatbots`, `automacao-de-whatsapp`, `geracao-de-conteudo`, `agentes-e-workflows`), ~12 integrações, ~15 tags e **20 produtos** publicados distribuídos entre as subcategorias, cada um com conteúdo completo nos dois idiomas e `source_note` apontando para a página de pricing consultada.
- Os 20 produtos devem ser produtos reais, com dados coletados de páginas públicas. Onde um dado não for público, deixar nulo — nunca inventar. **Origem (resolvido em 2026-08-26):** Claude Code propõe a lista, coleta os dados das páginas públicas de pricing e preenche `source_note` com a URL e a data da coleta; Wesley revisa antes do commit.
- Cada produto de seed inclui `hq_country` para que existam exemplos brasileiros e estrangeiros.

## 7. Infra e execução

- `Dockerfile` multi-stage (build com Maven, runtime com JRE 21 slim, usuário não-root).
- `docker-compose.yml`: serviços `db` (postgres:16 com `pgvector/pgvector:pg16` como imagem para já ter a extensão) e `app`. Volume nomeado para o banco. `app` depende de `db` com healthcheck.
- Variáveis: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USER`, `ADMIN_PASSWORD`, `SEED_ENABLED`, `APP_DEFAULT_LOCALE`. `.env.example` versionado; `.env` ignorado.
- Migração `V1__init.sql` cria extensões (`unaccent`, `pg_trgm`, `vector`), tabelas, enums, índices e as funções/triggers de tsvector.
- Profile `local` com `docker compose` e profile `test` com Testcontainers. Sem H2.

## 8. Critérios de aceite

Cada item vira ao menos um teste automatizado, salvo indicação de "manual".

**Infra**
- [ ] AC-01 `docker compose up --build` a partir de clone limpo sobe app e banco; `/actuator/health` retorna `UP` em ≤ 90 s. (manual, documentado no README)
- [ ] AC-02 Reiniciar os containers não duplica o seed.

**Categorias**
- [ ] AC-03 `GET /categories?lang=en` retorna nomes em inglês e `product_count` correto (só publicados, somando as subcategorias).
- [ ] AC-04 `GET /categories/atendimento-e-chatbots` e `GET /categories/customer-support-and-chatbots` retornam a mesma categoria.

**Produtos — leitura**
- [ ] AC-05 `GET /products` sem filtros retorna só PUBLISHED, paginado, `total` correto.
- [ ] AC-06 `GET /products/{slug}` de um DRAFT retorna 404 com Problem Details.
- [ ] AC-07 `GET /products/{slug}?lang=en` retorna `locale: "en"` e nenhum campo em português.
- [ ] AC-08 Header `Accept-Language: en` sem `lang` produz o mesmo resultado de `?lang=en`; `?lang=pt-BR` vence o header.
- [ ] AC-09 `?lang=fr` retorna 400; `Accept-Language: fr-FR,fr;q=0.9` (sem `lang`) cai no default `pt-BR` sem erro.

**Busca**
- [ ] AC-10 `q=atendimento` (pt-BR) retorna produtos da subcategoria de atendimento nas primeiras posições; `q=customer support` (en) retorna o mesmo conjunto. Em ambos, `matched_by = "FULL_TEXT"`.
- [ ] AC-11 Busca é insensível a acento: `q=automacao` ≡ `q=automação`.
- [ ] AC-12 Busca por nome com erro de digitação de 1 caractere ainda encontra o produto (via fallback trigram) e a resposta traz `matched_by = "TRIGRAM"`.
- [ ] AC-13 Filtros combinam: `category=X&integration=whatsapp&has_free_trial=true` retorna apenas produtos que satisfazem todos.
- [ ] AC-14 `price_max=100` não retorna produtos com `price_min > 100` e ignora produtos sem preço.
- [ ] AC-15 `size=51` retorna 400.

**Comparação**
- [ ] AC-16 `compare?slugs=a,b,c` retorna 3 fichas na ordem pedida; com 1 ou 4 slugs retorna 400; com slug inexistente retorna 404 indicando qual.

**Admin**
- [ ] AC-17 Qualquer rota `/admin/**` sem credenciais retorna 401; com credenciais erradas 401.
- [ ] AC-18 `POST /admin/products` com corpo faltando `tagline_en` retorna 400 com `errors[]` apontando o campo.
- [ ] AC-19 Publicar produto sem `product_content` em `en` retorna 422 listando a pendência; após preencher, publica e `published_at` é setado.
- [ ] AC-20 Mudar `slug` de produto PUBLISHED retorna 409.
- [ ] AC-21 `DELETE /admin/categories/{id}` com produtos vinculados retorna 409.
- [ ] AC-22 Produto atualizado via admin reflete imediatamente na busca (tsvector recalculado).

**Qualidade**
- [ ] AC-23 Todos os endpoints aparecem no Swagger com exemplos de request/response.
- [ ] AC-24 Nenhum log contém `ADMIN_PASSWORD` ou `DB_PASSWORD`.

## 9. Decisões já tomadas (não reabrir nesta spec)

- Postgres FTS em vez de Elasticsearch: volume pequeno, um banco só, custo zero adicional.
- Conteúdo bilíngue em colunas `_pt/_en` para campos curtos e tabela `product_content` por locale para conteúdo longo — evita joins para a listagem e mantém a ficha flexível.
- HTTP Basic para admin é temporário e explicitamente descartável na spec de identidade.
- Slug de produto único (não por idioma) porque nome de produto não se traduz; slug de categoria por idioma porque categoria se traduz e o SEO precisa da URL localizada.

Incorporadas em 2026-08-26 a partir do `plan.md` §8 (desvios aprovados):

| # | Decisão |
|---|---|
| D-01 | Tabela `category_slug` para unicidade global de slug entre idiomas (§4.1.1) |
| D-02 | `product_count` inclui subcategorias, para bater com o filtro `category=` (§5.1) |
| D-03 | `Accept-Language` sem idioma suportado cai no default; só `lang` explícito inválido dá 400 (§5) |
| D-04 | Coerência `price_min` × `pricing_model` validada na publicação, não como `CHECK` (§4.2) |
| D-05 | `q` usa `websearch_to_tsquery` — `to_tsquery` estoura com entrada livre, `plainto_tsquery` ignora aspas e negação (§4.6) |
| D-06 | Campo `matched_by` na resposta de `GET /products` (§5.1) |
| D-07 | JSON e query params em `snake_case` (§5) |

## 10. Perguntas resolvidas

Fechadas em 2026-08-26. Nenhuma pergunta em aberto.

| Pergunta | Decisão |
|---|---|
| Nome do projeto / pacote raiz | `br.com.saasfinder` — desacopla o pacote do nome comercial, que ainda pode mudar |
| Origem dos 20 produtos do seed (§6) | Claude Code pesquisa páginas públicas de pricing, monta os YAML com `source_note` (URL + data de coleta) e **Wesley revisa antes do commit**. Dado não público fica `null`, nunca inventado |
| Maven ou Gradle | **Maven**, confirmando a constituição §3. Wrapper (`mvnw`) versionado |

## 10.1 Emendas posteriores ao fechamento

| Quando | O quê | Por quê |
|---|---|---|
| 2026-08-27 | **Correção de defeito no OpenAPI:** o documento descrevia os campos em `camelCase` (`productCount`, `sortOrder`, `priceMin`) enquanto a API responde em `snake_case` (desvio D-07), e omitia `children` de `CategoryNodeView` | O springdoc montava os schemas a partir dos nomes dos componentes dos records, ignorando a estratégia do Jackson; e o swagger-core descarta a propriedade recursiva de um record quando ela não é declarada explicitamente. Nada falhava — o Swagger abria e os 223 testes passavam —, e o defeito só atingia quem gerasse cliente a partir do contrato: compilava e lia `undefined`. Encontrado ao gerar os tipos do frontend (spec 002). Corrigido com um `ModelResolver` construído com o `ObjectMapper` da aplicação e `@ArraySchema` explícito em `children`. Coberto por `OpenApiFidelidadeIT`, que compara o schema com o corpo servido a cada build. |
| 2026-08-27 | **Correção de defeito:** a resposta 200 de `GET /products` não declarava schema no OpenAPI, só um exemplo | `@Content` escrito à mão substitui o schema que o springdoc deduziria, e faltava `schema = @Schema(implementation = ProductListResponse.class)`. Quem gerasse cliente recebia `unknown` no endpoint mais usado da API. Encontrado ao escrever a lista de categoria (spec 002, T16). Coberto por `OpenApiFidelidadeIT`, que agora varre **todas** as respostas de sucesso do documento. |
| 2026-08-27 | **`GET /products`, `GET /products/{slug}` e `GET /products/compare` passam a trazer `rating`**: `{ average, count, recommend_ratio, verified_count }`, ou `null` quando o produto ainda não tem review publicada | A nota precisa aparecer onde as pessoas olham — no card e na ficha —, e o catálogo é quem serve as duas. O agregado é calculado na consulta, sem coluna denormalizada (spec 003 §4.4), e chega pelo `ReviewFacade.notasDe(Collection<UUID>)`: **uma consulta por página**, nunca uma por produto. `null` e não `0` porque nota zero é uma avaliação péssima e ausência de nota é outra coisa. `ProductSummaryView` ganhou também um `id` **não serializado**, para o `catalog` pedir as notas da página sem uma segunda ida ao banco só para traduzir slugs em ids; a API pública continua falando por slug. Implementado em `[003-T09]`, coberto por `ProdutoNotaIT`, que conta as consultas de uma listagem de 20 produtos. |
| 2026-08-27 | `GET /categories/{slug}` passou a devolver `alternates: {"pt-BR": ..., "en": ...}` | A spec 002 (frontend) precisa do slug da categoria no outro idioma para montar o seletor de idioma e o `hreflang`; sem o campo, seria uma segunda chamada por página rastreada. Projeção de `slug_pt`/`slug_en`, **sem migração**. Não vale para produto, que tem um slug só (§4.2). Implementado em `[002-T07]`, coberto por `CategoriaAlternatesIT`. |

## 11. Próximas specs (para contexto, não implementar)

- **002-frontend-catalogo** — Next.js SSR bilíngue consumindo esta API.
- **003-reviews** — reviews, verificação por domínio, moderação.
- **004-ranking-v1** — snapshot de score por categoria, página "como ranqueamos".
- **005-assistente-rag** — ingestão de `product_content` em `knowledge_chunk` com pgvector, chat com streaming.
- **006-identidade** — substitui HTTP Basic; usuários, magic link, domínio corporativo.
