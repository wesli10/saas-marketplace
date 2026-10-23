# Tasks 001 — Catálogo core

| Campo | Valor |
|---|---|
| Spec | `specs/001-catalogo-core/spec.md` |
| Plano | `specs/001-catalogo-core/plan.md` |
| Constituição | `CLAUDE.md` (raiz) |
| Total | 30 tarefas (T00–T29) — **todas concluídas** em 2026-08-26 |
| Data | 2026-08-26 |

## Como usar

- **Uma tarefa por commit.** Mensagem começa com `[001-TNN]`. Ex.: `[001-T05] cria tabelas de categoria com guarda de profundidade`.
- Uma tarefa só está pronta quando `./mvnw verify` passa **e** o critério de verificação dela foi observado.
- Se a implementação mostrar que a spec está errada, **para**, atualiza `spec.md`, depois continua (constituição §2.5).
- Tarefa que precisar de dependência nova exige atualizar a tabela do `plan.md` §DT-02 no mesmo commit.
- Legenda de dependência: `T00 ✅ (D-0n)` = dependia de um desvio, já aprovado e incorporado à spec em 2026-08-26.

---

## Fase 0 — Gate de spec

### T00 — Atualizar a spec com clarificações e desvios aprovados ✅
**Status:** concluída em 2026-08-26 — D-01..D-07 aprovados por Wesley e incorporados; `spec.md` sem nenhum `[NEEDS CLARIFICATION]`.
**Objetivo:** deixar a spec como fonte da verdade antes de qualquer código.
**Entrega:** `spec.md` §4 (tabela `category_slug`), §5 (campo `matched_by`, regra de `Accept-Language`, `snake_case`), §10 substituída pelas decisões fechadas (`br.com.saasfinder`, Maven, seed proposto por Claude e revisado por Wesley).
**Verificação:** `grep -c "NEEDS CLARIFICATION" spec.md` → `0`. Cada desvio aprovado aparece no corpo da spec, não só no plano.
**ACs:** — (processo)
**Depende de:** aprovação de D-01..D-07 (obtida).

---

## Fase A — Esqueleto que roda

### T01 — Projeto Maven mínimo que sobe ✅
**Status:** concluída em 2026-08-26 — `./mvnw verify` BUILD SUCCESS (2 testes), jar sobe e `/actuator/health` responde `UP`.
**Objetivo:** app Spring Boot compilando e respondendo health, sem banco ainda.
**Entrega:** `pom.xml` (Java 21, Spring Boot 3.5.x, `web` + `actuator`), wrapper `mvnw`, `SaasFinderApplication`, `application.yml`, `.editorconfig`, `.gitignore` de Java.
**Verificação:** `./mvnw verify` verde; `./mvnw spring-boot:run` sobe e `curl localhost:8080/actuator/health` → `{"status":"UP"}`. Virou teste automatizado em `SaasFinderApplicationTest` (health `UP` + `/actuator/env` 404), renomeado para `*IT` no T02.
**ACs:** —
**Depende de:** —

### T02 — Build endurecido: Spotless, JaCoCo, Failsafe ✅
**Status:** concluída em 2026-08-26 — teste negativo executado: com o arquivo desformatado o `verify` falha nomeando `SaasFinderApplication.java`; `spotless:apply` corrige e o build volta a passar com os 2 ITs rodando.
**Objetivo:** a "análise estática configurada" do DoD e a separação `*Test`/`*IT`.
**Entrega:** `spotless-maven-plugin` (palantir-java-format) ligado à fase `validate` — continua quebrando o `mvn verify`, mas em segundos em vez de depois da suíte —, `jacoco-maven-plugin` (relatório único alimentado por surefire e failsafe), `maven-failsafe-plugin` declarado **sem `<executions>`** (o parent só o gerencia em `<pluginManagement>`) e `SaasFinderApplicationTest` renomeado para `SaasFinderApplicationIT`.
**Verificação:** desformatar um arquivo de propósito → `./mvnw verify` falha apontando o arquivo; `./mvnw spotless:apply` corrige e o build volta a passar. `target/site/jacoco/index.html` existe.
**ACs:** —
**Depende de:** T01

### T03 — Docker e compose ✅
**Status:** concluída em 2026-08-26 — health `UP` em **13 s** (limite 90 s), container rodando como `uid=1001(app)`, volume sobrevivendo a `down`/`up`.
**Objetivo:** subir tudo com um comando.
**Entrega:** `Dockerfile` multi-stage (build Maven → runtime `21-jre-jammy`, usuário não-root, `MaxRAMPercentage=75`), `docker-compose.yml` (`db` = `pgvector/pgvector:pg16` com healthcheck `pg_isready` e volume nomeado; `app` com `depends_on: service_healthy`), `.env.example`.
**Verificação:** `docker compose up --build` de clone limpo; health `UP` em ≤ 90 s; `docker compose exec app id -u` retorna diferente de `0`; `docker compose down && up` preserva o volume.
**ACs:** AC-01 (parcial — reconfirmado no T29)
**Depende de:** T01

---

## Fase B — Banco

### T04 — Testcontainers, datasource e extensões ✅
**Status:** concluída em 2026-08-26 — `./mvnw verify` nativo em 19 s com 5 testes; container `pgvector:pg16` de verdade; `compose` continua subindo com health `UP` em 7 s.
**Objetivo:** todo teste de integração roda contra Postgres real.
**Entrega:** dependências `data-jpa`, `flyway-core`, `flyway-database-postgresql`, driver, Testcontainers; `AbstractIntegrationTest` com container `static` + `@ServiceConnection`; `application-test.yml`; `V1__init.sql` criando `unaccent`, `pg_trgm`, `vector`.
**Verificação:** um IT sobe o contexto e afirma que as 3 extensões existem em `pg_extension`. Nenhuma referência a H2 no projeto (`grep -ri h2 src/` vazio).
**ACs:** —
**Depende de:** T01

### T05 — `V2`: categorias, lookup de slug e guarda de profundidade ✅
**Status:** concluída em 2026-08-26 — 13 testes em `CategoriaSchemaIT`, suíte total em 18.
**Objetivo:** modelo de categoria completo no banco.
**Entrega:** `V2__categorias.sql` (não `V1`: ela foi commitada no T04 e migração commitada é imutável, constituição §5): tabela `category`, tabela `category_slug`, índices, `set_updated_at` compartilhada, triggers `category_depth_guard`, `category_slug_imutavel` e `category_slug_sync`.
**Verificação:** IT de SQL puro — (a) inserir categoria neta (profundidade 3) falha, e também falha pelo outro lado (categoria com filhas virando subcategoria); (b) inserir categoria cujo `slug_en` colide com `slug_pt` de outra falha na PK de `category_slug`; (c) alterar slug de categoria é **recusado** — a spec §4.1 diz que o slug é imutável após a criação, então não há o que propagar (o critério original dizia o contrário e estava errado).
**Nota para T06–T08:** pelo mesmo motivo da constituição §5, as tabelas restantes entram como `V3`, `V4` e `V5`, não emendando a `V1`.
**ACs:** — (habilita AC-04)
**Depende de:** T04, T00 ✅ (D-01)

### T06 — `V3`: produto e conteúdo por locale ✅
**Status:** concluída em 2026-08-26 — 19 testes em `ProdutoSchemaIT`, suíte total em 37.
**Objetivo:** tabelas `product` e `product_content`.
**Entrega:** colunas conforme spec §4.2/§4.3, `CHECK` de domínio (`status`, `pricing_model`, `price_period`, `locale`), `CHECK` de moeda, FKs, índices de `slug`/`category_id`/`status`/`price_min`.
**Verificação:** IT de SQL — (a) produto `DRAFT` sem `price_min` insere normalmente; (b) `price_min` sem `price_currency` falha; (c) `status` inválido falha; (d) `slug` duplicado falha.
**ACs:** — (habilita AC-05, AC-20)
**Depende de:** T05, T00 ✅ (D-04)

### T07 — `V4`: integrações, tags e as duas N:N ✅
**Status:** concluída em 2026-08-26 — 8 testes em `IntegracaoTagSchemaIT`, suíte total em 45.
**Entrega:** `integration`, `tag`, `product_integration`, `product_tag` com PKs compostas e índices inversos.
**Verificação:** IT de SQL — vínculo duplicado falha; apagar produto remove os vínculos (CASCADE); apagar categoria com produto falha (RESTRICT).
**ACs:** — (habilita AC-13, AC-21)
**Depende de:** T06

### T08 — `V5`: `tsvector`, índices de busca e triggers de recálculo ✅
**Status:** concluída em 2026-08-26 — 11 testes em `BuscaVetorSchemaIT`, suíte total em 56.
**Objetivo:** a busca ser consistente por responsabilidade do banco.
**Entrega:** colunas `search_vector_pt`/`search_vector_en`, índices GIN, índice `gin_trgm_ops` em `name`, função `catalog_refresh_product_search_vector(uuid)` com pesos A/B/C/D e `unaccent`, e os 5 triggers (`product`, `product_tag`, `product_integration`, `tag`, `integration`).
**Verificação:** IT de SQL — (a) inserir produto preenche os dois vetores; (b) vincular tag recalcula; (c) renomear integração recalcula os produtos ligados; (d) `to_tsquery` com termo sem acento casa com texto acentuado.
**ACs:** AC-11, AC-22 (nível banco; reconfirmado no T23)
**Depende de:** T07

### T09 — Entidades JPA, repositórios e UUID v7 ✅
**Status:** concluída em 2026-08-26 — 5 testes em `PersistenciaCatalogoIT`, suíte total em 61. `locale` de `product_content` segue como texto: o `AppLocale` nasceu no T11, mas convertê-lo dentro de uma chave composta é mexida que só se paga quando os endpoints de conteúdo chegarem (T18, T24).
**Entrega:** `IdGenerator` (`java-uuid-generator`), entidades `Category`, `Product`, `ProductContent`, `Integration`, `Tag`, enums Java, repositórios Spring Data. `text[]` com `SqlTypes.ARRAY`, `faq` com `SqlTypes.JSON`.
**Verificação:** IT de round-trip: salva e relê produto com `supported_languages`, `pros`, `cons` e `faq` preenchidos, tudo igual. Dois ids gerados em sequência são crescentes na ordenação de string.
**ACs:** —
**Depende de:** T08

---

## Fase C — Fundações transversais

### T10 — Erros no formato RFC 9457 ✅
**Status:** concluída em 2026-08-26 — 7 testes em `ProblemDetailsIT`, suíte total em 68.
**Entrega:** `shared/error`: `GlobalExceptionHandler`, `ProblemType`/`ProblemTypes`, hierarquia `ApiException` (`NotFound`, `Conflict`, `InvalidParameter`, `NotPublishable`, `UnsupportedLocale`), `ApiFieldError`, `messages.properties` (pt-BR, é o fallback padrão) e `messages_en.properties` — um arquivo `messages_pt_BR.properties` separado só duplicaria o mesmo conteúdo.
**Verificação:** IT — `GET /api/v1/rota-inexistente` devolve 404 com `Content-Type: application/problem+json` e corpo contendo `type`, `title`, `status`, `instance`.
**ACs:** —
**Depende de:** T01

### T11 — Resolução de idioma ✅
**Status:** concluída em 2026-08-26 — 9 testes em `LocaleResolutionIT`, suíte total em 77.
**Entrega:** `AppLocale` (código + dicionário FTS), `RequestLocale` request-scoped, `LocaleResolutionFilter` (`?lang` > `Accept-Language` com q-values > default), header `Content-Language` na resposta, `APP_DEFAULT_LOCALE` configurável. Controller de eco **em `src/test`** para exercitar o filtro.
**Verificação:** ITs — `?lang=en` e `Accept-Language: en` dão o mesmo resultado; `?lang=pt-BR` com header `en` vence o header; `?lang=fr` → 400 problem+json; `Accept-Language: fr-FR,fr;q=0.9` cai no default sem erro; toda resposta traz `Content-Language`.
**ACs:** AC-08, AC-09
**Depende de:** T10, T00 ✅ (D-03)

### T12 — Segurança do admin ✅
**Status:** concluída em 2026-08-26 — 8 ITs em `SecurityIT` mais 2 unitários em `SecurityConfigTest`, suíte total em 85.
**Entrega:** `SecurityConfig` (stateless, CSRF off, `/admin/**` com `ROLE_ADMIN` via Basic, resto `permitAll`), `InMemoryUserDetailsManager` a partir de `ADMIN_USER`/`ADMIN_PASSWORD` com BCrypt, `AuthenticationEntryPoint` devolvendo 401 problem+json. App falha ao subir se as variáveis não existirem — sem default embutido.
**Verificação:** ITs com rota `/admin/ping` de teste — sem credencial → 401 problem+json; credencial errada → 401; credencial certa → 200. Teste de contexto: sem `ADMIN_PASSWORD`, o boot falha com mensagem clara. Rota pública continua acessível sem auth.
**ACs:** AC-17
**Depende de:** T10

---

## Fase D — Leitura pública

### T13 — `GET /categories` e `GET /categories/{slug}` ✅
**Status:** concluída em 2026-08-26 — 9 testes em `CategoryControllerIT`. Executada em paralelo ao T14, em worktree isolado.
**Entrega:** `CategoryController`, DTOs de árvore, agregação de `product_count` (publicados, incluindo subcategorias), resolução de slug bilíngue via `category_slug`, `CatalogFacade`.
**Verificação:** ITs — `?lang=en` traz nomes em inglês e contagem correta; slug pt e slug en da mesma categoria devolvem o mesmo recurso; categoria raiz soma os filhos; produto `DRAFT` não entra na contagem; slug inexistente → 404.
**ACs:** AC-03, AC-04
**Depende de:** T09, T11, T00 ✅ (D-02)

### T14 — `GET /products`: listagem, paginação e envelope ✅
**Status:** concluída em 2026-08-26 — 9 testes em `ProductListIT`, suíte total em 103. O eco dos filtros (`query`) entra no T15, junto com os filtros que ele tem para ecoar.
**Entrega:** módulo `search` — `ProductSearchQuery`, `SearchSqlBuilder`, `ProductSearchJdbcRepository` (só `PUBLISHED`, ordem `updated_at DESC`, `count(*) OVER ()`), `PageResponse`, `PagingParams` com limite de `size`, `ProductController#list`, `SearchFacade`.
**Verificação:** ITs — retorna só publicados; `total` bate com o número real; `page`/`size` paginam sem sobreposição; `size=51` → 400 problem+json; `size=50` passa; sem filtro, itens vêm ordenados por `updated_at` decrescente.
**ACs:** AC-05, AC-15
**Depende de:** T09, T11
**Nota:** carregar integrações em lote (`WHERE product_id IN (...)`), nunca lazy em laço.

### T15 — Filtros de `GET /products` ✅
**Status:** concluída em 2026-08-26 — 10 ITs em `ProdutoFiltroIT` e 6 unitários em `SearchSqlBuilderTest`, suíte total em 113.
**Entrega:** `category` (incluindo subcategorias), `integration` repetível com `AND`, `pricing_model` repetível com `OR`, `has_free_trial`, `has_free_plan`, `price_max`, e o eco em `query`.
**Verificação:** ITs — `category=X&integration=whatsapp&has_free_trial=true` só devolve quem satisfaz os três; dois `integration` exigem ambos; dois `pricing_model` aceitam qualquer um; `price_max=100` exclui `price_min > 100` **e** exclui produto sem preço; filtro por categoria pai traz produtos das filhas; `query` ecoa exatamente o que foi aplicado.
**ACs:** AC-13, AC-14
**Depende de:** T14

### T16 — Busca full-text ✅
**Status:** concluída em 2026-08-26 — 10 ITs em `ProdutoBuscaIT` e 3 unitários novos em `SearchSqlBuilderTest`, suíte total em 123.
**Entrega:** `q` → `websearch_to_tsquery(dicionário, unaccent(q))` no vetor do locale, ordenação `ts_rank_cd(..., 32) DESC, updated_at DESC`, campo `matched_by`.
**Verificação:** ITs com fixtures bilíngues controladas — `q=atendimento` (pt-BR) traz os produtos de atendimento nas primeiras posições; `q=customer support` (en) traz o mesmo conjunto; `q=automacao` ≡ `q=automação`; `matched_by = "FULL_TEXT"`; `q` com aspas ou caractere solto (`"`, `&`, `:`) não estoura 500.
**ACs:** AC-10, AC-11
**Depende de:** T15, T00 ✅ (D-05, D-06)

### T17 — Fallback por similaridade trigram ✅
**Status:** concluída em 2026-08-26 — 6 ITs novos em `ProdutoBuscaIT` e 3 unitários novos em `SearchSqlBuilderTest`, suíte total em 129.
**Nota de implementação:** o predicado `similarity(...) > 0.3` **não** usa o índice `product_name_trgm_idx` (só o operador `%` o usaria, e ele depende do GUC de sessão, plan.md §DT-07). Fica assim de propósito: o caminho só roda quando a busca principal já devolveu zero linhas.
**Entrega:** quando o FTS devolve 0 linhas, segunda query com `similarity(unaccent(name), unaccent(q)) > 0.3`, ordenada por similaridade, `matched_by = "TRIGRAM"`; nenhum resultado nos dois → `matched_by = "NONE"`.
**Verificação:** ITs — nome com 1 caractere trocado ainda encontra o produto; termo que casa no FTS **não** dispara o fallback (`matched_by = "FULL_TEXT"`); string sem relação devolve lista vazia com `matched_by = "NONE"`.
**ACs:** AC-12
**Depende de:** T16

### T18 — `GET /products/{slug}` ✅
**Status:** concluída em 2026-08-26 — 9 ITs em `ProdutoFichaIT`, suíte total em 138.
**Notas de implementação:**
- São **duas** consultas por ficha, não uma: o agregado (produto + categoria + tags + integrações, via `join fetch`) e a linha de `product_content` do idioma pedido. `product_content` não é coleção mapeada de `Product` porque a ficha quer um idioma só — trazer os dois para descartar um seria trabalho jogado fora em toda requisição. O que o DT-13 proíbe (acesso lazy dentro de laço) continua valendo.
- `ProductDetailView` reaproveita `CategoriaResumo` e `IntegracaoResumo` do módulo `search`: é o mesmo par `{slug, name}` do item de lista, e a dependência `catalog → search` já existia (o `ProductController` fala com o `SearchFacade`).
- `source_note` não é ocultado por anotação: ele simplesmente não existe no record da ficha. Campo que não existe não vaza por descuido de serialização.
**Entrega:** ficha completa resolvida pelo locale (tagline, description, pros, cons, best_for, faq), com tags e integrações; `source_note` nunca serializado; 404 para `DRAFT`/`ARCHIVED`.
**Verificação:** ITs — produto `DRAFT` → 404 problem+json; `?lang=en` devolve `locale: "en"` e nenhum texto em português no corpo; resposta não contém a string de `source_note`; produto sem `product_content` no locale pedido responde com os campos longos vazios, sem 500.
**ACs:** AC-06, AC-07
**Depende de:** T14

### T19 — `GET /products/compare` ✅
**Status:** concluída em 2026-08-26 — 10 ITs em `ProdutoComparacaoIT`, suíte total em 148.
**Notas de implementação:**
- `ApiException` ganhou `comExtensao(chave, valor)` e o `ProblemDetailFactory` passou a escrever essas chaves no corpo. É o mecanismo genérico de extensão da RFC 9457; `missing_slugs` é o primeiro uso, o 422 de publicação (T24) provavelmente será o segundo.
- Slug ausente derruba a resposta inteira em vez de sumir da lista: comparação com menos produtos do que o cliente pediu seria resposta errada disfarçada de certa.
- **Validado é a quantidade, não a unicidade.** `slugs=a,a,a` cai fora da faixa e vira 400 por contagem; `slugs=a,a` devolve a mesma ficha duas vezes. A spec não pede desduplicação e não inventei a regra.
- A ficha é montada por um `paraFicha(...)` compartilhado com `GET /products/{slug}` — os dois endpoints não podem divergir de formato.
**Entrega:** 2 a 3 slugs, mesma estrutura da ficha, na ordem pedida; 400 fora da faixa; 404 com extensão `missing_slugs`.
**Verificação:** ITs — 3 slugs devolvem 3 fichas **na ordem da query**; 1 slug → 400; 4 slugs → 400; slug inexistente → 404 com `missing_slugs` nomeando qual; slug de produto `DRAFT` conta como inexistente.
**ACs:** AC-16
**Depende de:** T18

### T20 — `GET /integrations` ✅
**Status:** concluída em 2026-08-26 — 5 ITs em `IntegracaoListaIT`, suíte total em 153. Fecha a Fase D (leitura pública).
**Notas de implementação:**
- A contagem é subconsulta correlacionada, não `join` com `group by`: assim integração sem nenhum produto publicado aparece com `0` em vez de sumir do resultado. Continua sendo um único SELECT.
- Envelope **sem `locale`** (plan.md §4.6): nome de integração é nome próprio e não se traduz, então não há idioma a resolver. É a única lista pública sem esse campo, e de propósito.
**Entrega:** lista com `slug`, `name` e `product_count` (só publicados).
**Verificação:** IT — contagem bate; integração sem produto publicado aparece com `0`.
**ACs:** —
**Depende de:** T14

---

## Fase E — Admin

### T21 — CRUD de categorias ✅
**Status:** concluída em 2026-08-26 — 13 ITs em `AdminCategoriaIT`, suíte total em 166. Abre a Fase E.
**Notas de implementação:**
- **Desvio do diagrama do plano:** o serviço de escrita virou `catalog/domain/CatalogWriteService`, e não `admin/domain/AdminCatalogService`. A constituição §4 proíbe um módulo de acessar repositório de outro, e as invariantes (profundidade, unicidade de slug, guardas de exclusão) são do catálogo, não da política de admin. O `plan.md` foi atualizado (diagrama, fronteiras e matriz de rastreabilidade).
- A validação de aplicação **não substitui** os triggers da V2 — duplica de propósito, para o cliente receber 409 explicado em vez de 500 de violação de constraint.
- `PUT` com slug diferente do atual responde **409**, em vez de ignorar o campo em silêncio: o campo existe no corpo para caber no POST, e aceitar-e-descartar faria o curador achar que a mudança valeu.
- Pai inexistente → **404**; pai que já é subcategoria → **409** (a tarefa deixava "400/409" em aberto; 409 porque o problema é o estado da hierarquia, não a sintaxe do parâmetro).
- **Correção de formato:** `errors[]` passou a nomear os campos em `snake_case` (`tagline_en`), como o cliente os envia (D-07), e não com o nome da propriedade Java. Os erros manuais já saíam assim (`product_content.en`); só os do Bean Validation destoavam. A expectativa antiga em `ProblemDetailsIT` foi atualizada — ela codificava a inconsistência.
**Entrega:** `AdminCategoryController` (GET, POST, PUT, DELETE), `AdminCatalogService`, guarda de exclusão, validação de profundidade e de colisão de slug entre idiomas.
**Verificação:** ITs — DELETE de categoria com produto → 409 problem+json; DELETE com subcategoria → 409; DELETE de categoria vazia → 204; POST com pai que já tem pai → 400/409; POST com slug colidindo → 409; todas as rotas exigem Basic.
**ACs:** AC-21
**Depende de:** T12, T13

### T22 — CRUD de integrações e tags ✅
**Status:** concluída em 2026-08-26 — 7 ITs em `AdminVocabularioIT`, suíte total em 173.
**Notas de implementação:**
- As escritas entraram no mesmo `CatalogWriteService` do T21, pelo mesmo motivo: as invariantes (unicidade de slug) são do catálogo.
- `AdminIntegrationResponse` **não** traz `product_count` — a contagem é assunto da rota pública, e quem precisa dela já tem `GET /api/v1/integrations`.
- `Integration` e `Tag` ganharam os getters de `created_at`/`updated_at`, que faltavam (as outras entidades já tinham).
**Entrega:** `AdminIntegrationController`, `AdminTagController` (GET, POST).
**Verificação:** ITs — criação com slug duplicado → 409; corpo inválido → 400 com `errors[]`; sem credencial → 401.
**ACs:** —
**Depende de:** T21

### T23 — CRUD de produtos ✅
**Status:** concluída em 2026-08-26 — 13 ITs em `AdminProdutoIT`, suíte total em 188.
**Notas de implementação:**
- A listagem sai em **duas consultas**: os ids da página e depois o agregado deles com os vínculos. Paginar direto com `join fetch` faria o Hibernate trazer tudo e recortar em memória — `limit` não se aplica quando cada produto ocupa várias linhas do resultado (DT-13).
- A visão de curadoria é montada **dentro** da transação (`ProductAdminView`, em `catalog/domain`), como a ficha pública: com `open-in-view: false` as coleções lazy não sobrevivem à saída do serviço.
- `PUT` **substitui** os vínculos em vez de acumular: o corpo descreve o produto inteiro, então slug que sumiu do corpo sumiu do produto.
- Slug em uso responde 409 também no `POST` e no `PUT` de `DRAFT` — a unique index já barraria, mas com 500.
- Vínculo com slug inexistente → **400** nomeando o slug (`invalid-parameter`), não 404: o recurso pedido é o produto, e ele existe; o que está errado é um valor do corpo.
- O que a publicação vai exigir (preço coerente, conteúdo nos dois locales) **não** é exigido aqui: rascunho incompleto é o fluxo normal do curador (D-04). Isso fica no T24.
- AC-22 verificado pela API de ponta a ponta: `PUT` na descrição de um publicado e a busca da requisição seguinte já encontra o termo novo, sem passo intermediário — o `tsvector` é recalculado por trigger (DT-06).
**Entrega:** `AdminProductController` (`GET ?status=`, `POST`, `PUT`), DTOs bilíngues com Bean Validation, vínculo por `integration_slugs`/`tag_slugs`, guarda de slug imutável em `PUBLISHED`, `source_note` obrigatório.
**Verificação:** ITs — `POST` sem `tagline_en` → 400 com `errors[]` apontando o campo; `POST` cria em `DRAFT`; `PUT` mudando slug de produto `PUBLISHED` → 409; `PUT` mudando slug de `DRAFT` funciona; `GET ?status=DRAFT` filtra; sem `status`, traz todos; **editar a descrição de um produto publicado altera o resultado da busca na requisição seguinte** (AC-22 pela API).
**ACs:** AC-18, AC-20, AC-22
**Depende de:** T22, T16

### T24 — Conteúdo por locale e ciclo de publicação ✅
**Status:** concluída em 2026-08-26 — 14 ITs em `AdminPublicacaoIT`, suíte total em 202. Fecha a Fase E.
**Notas de implementação:**
- O `ProductPublicationValidator` devolve a **lista inteira** de pendências, não a primeira: o curador corrige tudo de uma vez em vez de descobrir uma por requisição. Tem um IT só para isso.
- "Campos obrigatórios preenchidos" (DT-16) não virou verificação: todos eles são `NOT NULL` na V3 e já são exigidos pelo DTO, então não há o que faltar na publicação. `price_currency` idem — o CHECK `product_preco_exige_moeda` torna o caso impossível.
- **Transições:** `publish` aceita `DRAFT` e `PUBLISHED` (idempotente, sem mexer no `published_at`); `unpublish` só de `PUBLISHED`; `archive` de qualquer estado. `ARCHIVED` → `publish` é 409 — desarquivar não está na spec, e inventar a transição seria decidir por ela.
- `published_at` **não** é limpo no `unpublish`: a data marca a primeira vez que a ficha foi ao ar, e apagá-la perderia há quanto tempo o produto existe.
- `PUT .../content/{locale}` responde **204**: o corpo enviado já é a representação do que ficou gravado.
- **Corrigido durante a tarefa:** `published_at` passou a ser truncado para microssegundos. `Instant.now()` tem precisão de nanossegundos e `timestamptz` guarda microssegundos, então a resposta da primeira publicação prometia uma precisão que a leitura seguinte não confirmava. O IT de "publicar de novo" é quem pegou.
**Entrega:** `PUT /admin/products/{id}/content/{locale}` (substituição), `ProductPublicationValidator` (conteúdo nos dois locales, ≥ 1 idioma suportado, coerência preço × `pricing_model`, obrigatórios), `publish`/`unpublish`/`archive`.
**Verificação:** ITs — publicar sem `product_content` em `en` → 422 listando a pendência; preencher e publicar → 200 com `published_at` setado; publicar de novo não altera `published_at`; publicar `SUBSCRIPTION` sem `price_min` → 422; `unpublish` volta para `DRAFT` e some da rota pública; `archive` some da rota pública; transição inválida → 409; `locale` fora de {pt-BR, en} na rota → 400.
**ACs:** AC-19
**Depende de:** T23

---

## Fase F — Seed

### T25 — Loader de seed idempotente ✅
**Status:** concluída em 2026-08-26 — 7 ITs (`SeedIT` 5 + `SeedDesligadoIT` 2) e 5 unitários em `LeitorDeSeedTest`; suíte em 214 ITs e 21 unitários.
**Notas de implementação:**
- O loader **não escreve SQL próprio**: chama o mesmo `CatalogWriteService` que o admin usa. Assim passa pelas mesmas guardas e pelo mesmo validador de publicação — um caminho paralelo poderia publicar o que o admin recusaria (DT-16).
- Idempotência por **duas** travas (DT-15): `pg_advisory_xact_lock` contra duas instâncias subindo juntas, e a contagem contra o restart. Sozinha, a contagem teria a janela entre ler e escrever.
- **Corrigido depois de o CI quebrar:** a checagem de banco vazio olhava só `category` e `product`, como o DT-15 escrevia. No CI a ordem dos testes deixou integrações e tags gravadas com zero categorias, o banco passou por vazio e o seed derrubou o boot tentando recriar `whatsapp` (409). Agora a checagem cobre as quatro tabelas que o seed escreve, e há um IT com exatamente esse cenário. O `plan.md` foi atualizado.
- A leitura dos YAML saiu para `LeitorDeSeed`, separada da orquestração — mesmo motivo do `SearchSqlBuilder`: dá para testar sem banco, e é onde mora o risco de um arquivo mal escrito passar despercebido. O `LeitorDeSeedTest` valida **os arquivos versionados de verdade** (os dois idiomas, `source_note`, `content` nos dois locales, preço coerente), então um erro de digitação no seed quebra em segundos em vez de no boot.
- Campo desconhecido no YAML **falha**, não é ignorado: `nome_do_produto` em vez de `name` seria um campo silenciosamente perdido.
- **Os 4 produtos deste seed são fictícios e estão marcados como tal** (`Acme Chat`, `Zeta Flow`, `Orion Copy`, `Nimbus Agents`, com `source_note` começando em `EXEMPLO -`). A constituição §7.5 proíbe dado de produto inventado; um fornecedor claramente fake não se passa por real, e os 20 produtos reais do T26 substituem estes quatro. As categorias, integrações e tags, essas sim, são as definitivas da spec §6.
**Entrega:** `SeedRunner` (`ApplicationRunner`, ativo só com `SEED_ENABLED=true`), leitura YAML com Jackson, `pg_advisory_xact_lock` + checagem de banco vazio, reuso do `ProductPublicationValidator`. Estrutura de arquivos com as 5 categorias, ~12 integrações, ~15 tags e **4** produtos de exemplo.
**Verificação:** ITs — subir com `SEED_ENABLED=true` popula; subir de novo **não** duplica (contagens idênticas); `SEED_ENABLED=false` não escreve nada; arquivo de seed inválido derruba o boot com mensagem apontando o arquivo.
**ACs:** AC-02
**Depende de:** T24

### T26 — Os 20 produtos reais ✅
**Status:** concluída em 2026-08-26 — 20 produtos, 5 por subcategoria, revisados e aprovados por Wesley antes do commit. `SeedIT` em 6 ITs e suíte total em 217.
**Coleta:** cada `source_note` traz a URL da página de pricing lida e `coletado em 2026-08-26`. Nenhum valor foi escrito de memória (constituição §7.5).
**Trocas em relação à lista aprovada:** ManyChat, Canva e Botpress devolveram **403** ao fetch e a Huggy renderiza o preço por JS — sem conseguir ler a página, preencher o preço seria inventar. Entraram `respond.io`, `ElevenLabs` e `Relevance AI` no lugar. A `360dialog` saiu para a `Twilio` entrar, por decisão do Wesley: as duas são acesso cru à API do WhatsApp, e a Twilio faz o seed exercitar de verdade o `USAGE_BASED` que a `V6` destravou.
**Decisões do Wesley na revisão:**
- `supported_languages` registra o **idioma da página oficial consultada**, não uma lista verificada de locales de interface. Fica assim: verificação mais robusta não se paga nesta fatia.
- `hq_country` fica nulo nos três casos em que a sede não foi confirmada (Wati, Writesonic, respond.io). Campo não é crítico.
- `integrations` **não** é preenchida por dedução: só aparece onde o canal *é* o produto. Listas de integração não foram coletadas, então preenchê-las seria inventar.
- `price_min` é o **plano pago mais barato**, não o gratuito. Por isso a Zenvia aparece com R$ 600 mesmo tendo faixa de R$ 0 — o gratuito fica em `has_free_plan`.
- Freshdesk: a página cita um "Free Program for 2 users" sem detalhar condições; `has_free_plan` fica `false` e a ambiguidade está registrada nos `cons`.
**Três produtos sem preço publicado** (`take-blip`, `pipefy`, `relevance-ai`): a página não divulga, então `price_min` é nulo e o `pricing_model` explica o porquê. É o caminho de `null` da spec §6 exercitado com dado real.
**Achado que virou correção de spec:** a coleta da Twilio esbarrou em `numeric(12,2)`, que arredondava US$ 0,005 para o dobro. Virou a migração `V6` e o ajuste da spec §4.2, em commit próprio.
**Entrega:** `seed/products/*.yml` com 20 produtos publicados distribuídos nas 4 subcategorias, conteúdo completo em pt e en, `source_note` com URL da pricing page + data de coleta, `hq_country` com mistura de brasileiros e estrangeiros. Dado não público fica `null` — **nunca inventado** (constituição §7.5).
**Verificação:** IT de fumaça com o seed real: 20 produtos publicados, todos com conteúdo nos dois locales, todos com `source_note` não vazio, ≥ 1 produto por subcategoria, ≥ 1 com `hq_country = 'BR'` e ≥ 1 estrangeiro. **Revisão do Wesley antes do commit.**
**ACs:** — (spec §6)
**Depende de:** T25

---

## Fase G — Qualidade e fechamento

### T27 — OpenAPI completo ✅
**Status:** concluída em 2026-08-26 — 5 ITs em `OpenApiIT`, suíte em 213 ITs e 21 unitários. Executada fora de ordem (antes do T26), porque T26 depende da revisão do Wesley e T27 só dependia de os endpoints existirem.
**Notas de implementação:**
- O `OpenApiIT` compara o documento com o `RequestMappingHandlerMapping` **nos dois sentidos**: rota sem documentação falha, e documento anunciando rota inexistente também. Rota nova sem doc quebra o build no dia em que nasce.
- As respostas de erro (400, 500 e o 401 das rotas de curadoria) e o `@SecurityRequirement` do Basic entram por um `OperationCustomizer`, não por anotação repetida em cada método: a regra é global, e uma anotação esquecida num controller novo produziria documentação que mente sobre o comportamento.
- Exemplos de request ficam como `@Schema(example=...)` **no próprio record** de DTO — um exemplo completo por corpo, em vez de um por campo, e mora junto do contrato que ele ilustra.
- `@Qualifier("requestMappingHandlerMapping")` no teste: o actuator registra um segundo bean do mesmo tipo.
**Entrega:** `OpenApiConfig` (info, servidores, security scheme Basic), anotações `@Operation`/`@ApiResponse`/`@Schema` com exemplos de request e response em todos os endpoints, incluindo os corpos de erro.
**Verificação:** `OpenApiIT` compara as rotas de `/v3/api-docs` com as registradas no `RequestMappingHandlerMapping` e falha se faltar alguma; abrir `/swagger-ui.html` e executar `GET /products` pela UI (manual, no README).
**ACs:** AC-23
**Depende de:** T24

### T28 — Logs estruturados e redação de segredo ✅
**Status:** concluída em 2026-08-26 — 2 ITs em `LogRedactionIT`, suíte total em 175. O teste sobe a aplicação inteira num processo separado, com `ADMIN_USER`, `ADMIN_PASSWORD` e `DB_PASSWORD` como variáveis de ambiente de verdade: nenhum dos valores aparece na saída, e **todas** as linhas do boot são JSON válido com `timestamp`, `level`, `logger`, `message`. Teste negativo feito à mão pelo agente (`--spring.profiles.active=<valor de ADMIN_PASSWORD>`): o valor vaza para 1 linha e a asserção quebra — a guarda não passa de graça. `GET /actuator/env` → 404 continua coberto por `SaasFinderApplicationIT`, sem duplicar. Profile `local` conferido à mão: console legível, sem JSON.
**Integração (feita em paralelo):** a tarefa foi executada num worktree isolado e o commit chegou por cherry-pick. A base do worktree era antiga (anterior ao T04), então o `LogRedactionIT` original subia a aplicação sem banco e sem `ADMIN_USER`. Corrigido na integração: o teste passou a reaproveitar o Postgres do Testcontainers, a subir a app **inteira** (com web e security — `web-application-type=none` deixa o `HttpSecurity` sem bean) e a encerrar o processo assim que encontra o `Started SaasFinderApplication`, cortando a captura na quebra de linha seguinte para não sobrar JSON pela metade. A senha do container do `AbstractIntegrationTest` virou um valor distintivo: `"teste"` poderia casar por acaso e fazer a asserção de vazamento passar de graça.
**Entrega:** `logback-spring.xml` com `LogstashEncoder` (console legível no profile `local`), actuator expondo só `health`, revisão de que nenhum log imprime e-mail completo, token ou corpo de review.
**Verificação:** `LogRedactionIT` captura a saída do boot e falha se aparecer o valor de `ADMIN_PASSWORD` ou `DB_PASSWORD`; `GET /actuator/env` → 404; uma linha de log qualquer é JSON válido com `timestamp`, `level`, `logger`, `message`.
**ACs:** AC-24
**Depende de:** T27
**Notas de implementação:**
- O `LogRedactionIT` **não** é `@SpringBootTest`. O Spring Boot configura o Logback uma única vez por JVM (`isAlreadyInitialized`) e os contextos de `@SpringBootTest` ficam em cache abertos até o fim da suíte — trocar `System.out` no teste só funcionaria se ele fosse o primeiro IT a rodar. Processo separado também é o único jeito de as senhas serem variáveis de ambiente reais, e não apenas propriedades Spring.
- Nomes de campo do `LogstashEncoder` renomeados via `<fieldNames>`: o default é `@timestamp`/`logger_name`, e o AC-24 fala em `timestamp`/`logger`. `@version` e `level_value` foram descartados (`[ignore]`).
- Redação por **omissão**, não por máscara (plano DT-11): não se liga DEBUG nem se imprime propriedade de ambiente, então o segredo nunca chega ao log. Se uma spec futura precisar mascarar valor que passa por argumento de log, o caminho é o `MaskingJsonGeneratorDecorator` do encoder — fora do escopo desta tarefa.
- `application.yml` **não foi tocado**: a exposição só de `health` já veio do T01 e foi conferida.
- Ressalva: a parte de "nenhum log imprime e-mail completo, token ou corpo de review" **não foi revisada contra o código atual**. O agente a avaliou contra uma árvore antiga, onde só existia `SaasFinderApplication`. Hoje o código não loga nada além do `GlobalExceptionHandler` (que registra a URI e a exceção, sem corpo de requisição), mas reviews ainda não existem — essa metade do AC-24 precisa ser reconferida quando a spec de reviews chegar, e no fechamento do T29.

### T29 — README e fechamento do DoD ✅
**Status:** concluída em 2026-08-26 — `README.md` escrito e o DoD percorrido **contra o compose de verdade**, não no papel. Suíte final: 217 ITs e 21 unitários.
**Medido no container, de clone limpo com `docker compose down -v`:**
- health `UP` em **49 s** desde o início do build (limite do AC-01: 90 s);
- `docker compose exec app id -u` → **1001**, não-root;
- `down` seguido de `up` preserva o volume: os 20 produtos continuam lá;
- segundo boot **com `SEED_ENABLED=true`** registra `seed ignorado: o catalogo ja tem dados` e as contagens não mudam (AC-02 provado no container, não só no Testcontainers);
- nenhuma ocorrência de `DB_PASSWORD` nem de `ADMIN_PASSWORD` nos logs, e cada linha é JSON com `timestamp`, `level` e `logger` (AC-24);
- `/api/v1/admin/products` sem credencial → 401; `/swagger-ui/index.html` → 200; `/actuator/env` → 404.
**Achado na demo de sucesso, registrado no README:** `q=atendimento&lang=pt-BR` devolve **9** produtos e `q=customer support&lang=en` devolve **8**. A diferença é o `twilio-whatsapp`, cujo texto em pt usa "janela de **atendimento**" (o termo que a Meta usa em português) enquanto o inglês diz "service window". Os textos dos produtos são paralelos, não traduções literais, então a busca sobre dado real não é uma bijeção — o AC-10, que exige o mesmo conjunto, continua verificado com fixtures controladas em `ProdutoBuscaIT`. Preferi documentar a diferença a reescrever o texto do produto para a demo ficar bonita.
**Entrega:** `README.md` com pré-requisitos (Docker obrigatório para os testes), como rodar, como testar, variáveis de ambiente, o que a fatia entrega e os passos manuais de verificação; nota sobre `CREATE EXTENSION` exigir privilégio em Postgres gerenciado.
**Verificação:** de um clone limpo, seguindo **só** o README: `docker compose up --build` → health `UP` em ≤ 90 s → Swagger UI abre → `GET /api/v1/products?q=atendimento whatsapp&lang=pt-BR` devolve lista relevante → o mesmo com `?q=customer support&lang=en` devolve o mesmo conjunto em inglês. Percorrer o checklist do DoD (constituição §8) item a item.
**ACs:** AC-01 (final) + demo de sucesso da spec §1
**Depende de:** T28

---

## Matriz AC → tarefa

| AC | Tarefa(s) | Tipo |
|---|---|---|
| AC-01 | T03, T29 | Manual, documentado no README |
| AC-02 | T25 | `SeedIT` |
| AC-03 | T13 | IT |
| AC-04 | T05, T13 | IT |
| AC-05 | T14 | IT |
| AC-06 | T18 | IT |
| AC-07 | T18 | IT |
| AC-08 | T11 | IT |
| AC-09 | T11 | IT |
| AC-10 | T16 | IT |
| AC-11 | T08, T16 | IT |
| AC-12 | T17 | IT |
| AC-13 | T15 | IT |
| AC-14 | T15 | IT |
| AC-15 | T14 | IT |
| AC-16 | T19 | IT |
| AC-17 | T12 | IT |
| AC-18 | T23 | IT |
| AC-19 | T24 | IT |
| AC-20 | T23 | IT |
| AC-21 | T21 | IT |
| AC-22 | T08, T23 | IT |
| AC-23 | T27 | `OpenApiIT` + manual |
| AC-24 | T28 | `LogRedactionIT` |

Nenhum AC ficou sem tarefa. Nenhuma tarefa existe sem AC ou sem item explícito da spec.

## Caminho crítico

`T00 → T04 → T05 → T06 → T07 → T08 → T09 → T14 → T15 → T16 → T23 → T24 → T25 → T26 → T27 → T28 → T29`

Fora do caminho crítico e paralelizáveis: T02, T03, T10, T12, T18, T19, T20.

## Checklist do DoD (constituição §8) — fechado no T29 em 2026-08-26

- [x] `docker compose up --build` sobe do zero e health responde — **49 s**, contra o limite de 90 s
- [x] Todo critério de aceite tem teste automatizado ou passo manual no README — ver a matriz AC → tarefa acima; só o AC-01 é manual, e está documentado
- [x] `./mvnw verify` passa (build + testes + Spotless) — **217 ITs e 21 unitários**, zero falhas
- [x] Swagger UI reflete todos os endpoints — o `OpenApiIT` compara o documento com o `RequestMappingHandlerMapping` nos dois sentidos, então isso não depende de alguém lembrar de conferir
- [x] `README.md` explica rodar, testar e o que a fatia entrega
- [x] `spec.md` atualizada com todo desvio decidido durante a implementação — D-01..D-07 no corpo da spec, mais as correções do T16 (`matched_by` nulo sem busca), T18 (duas consultas na ficha), T21 (serviço de escrita no `catalog`), T25 (banco vazio = quatro tabelas) e T26 (`price_min` com quatro casas)
