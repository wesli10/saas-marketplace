# Plan 001 — Catálogo core

| Campo | Valor |
|---|---|
| Spec | `specs/001-catalogo-core/spec.md` |
| Constituição | `CLAUDE.md` (raiz) |
| Status | Proposto — aguardando aprovação dos desvios da seção 8 |
| Data | 2026-08-26 |
| Pacote raiz | `br.com.saasfinder` |
| Próximo artefato | `tasks.md` (esboço na seção 10) |

---

## 0. Perguntas em aberto da spec §10 — resolvidas

| Pergunta | Decisão | Ação na spec |
|---|---|---|
| Nome do pacote raiz | `br.com.saasfinder` — desacopla o pacote do nome comercial, que ainda pode mudar | Substituir o `[NEEDS CLARIFICATION]` por esta decisão |
| Origem dos 20 produtos do seed | Claude Code propõe: pesquisa páginas públicas de pricing, monta os arquivos YAML com `source_note` (URL + data de coleta) e Wesley revisa **antes** do commit. Dado não público fica `null` | Idem |
| Maven ou Gradle | **Maven**, confirmando a constituição §3. Wrapper (`mvnw`) versionado para o build não depender do Maven da máquina | Idem |

---

## 1. Decisões técnicas

### DT-01 — Build e runtime
Java 21 (release/toolchain fixados no `pom.xml`), Spring Boot 3.5.x, Maven com wrapper versionado.
Sem módulos Maven múltiplos: **um artefato só**, conforme constituição §4 (monolito modular). A modularidade é de pacote, não de build. Se um dia precisar ser imposta por ferramenta, entra ArchUnit numa spec futura — não agora.

### DT-02 — Dependências (constituição §7.1 exige justificativa)

| Dependência | Por quê |
|---|---|
| `spring-boot-starter-web` | API REST/JSON |
| `spring-boot-starter-validation` | Bean Validation nos DTOs (constituição §6, AC-18) |
| `spring-boot-starter-data-jpa` | CRUD de catálogo e admin |
| `spring-boot-starter-security` | HTTP Basic em `/admin/**` (spec §5.2, AC-17) |
| `spring-boot-starter-actuator` | `/actuator/health` com check do Postgres (spec §5.3, AC-01) |
| `org.postgresql:postgresql` | Driver |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | Migrações. O segundo artefato é obrigatório para Postgres a partir do Flyway 10 |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI e `/v3/api-docs` (AC-23) |
| `net.logstash.logback:logstash-logback-encoder` | Log estruturado em JSON (constituição §6) |
| `com.fasterxml.uuid:java-uuid-generator` | UUID v7 (constituição §5). Java 21 só gera v4; `uuidv7()` nativo do Postgres só existe do PG 18 em diante e a stack é PG 16 |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | Ler os arquivos de seed em YAML (não vem no starter) |
| *(test)* `spring-boot-starter-test` | JUnit 5, MockMvc, AssertJ |
| *(test)* `spring-security-test` | Autenticação nos testes de admin |
| *(test)* `org.testcontainers:postgresql` + `junit-jupiter` | Postgres real nos testes (constituição §7.3, sem H2) |
| *(plugin)* `spotless-maven-plugin` | Formatação verificada no `verify` — é a "análise estática configurada" do DoD |
| *(plugin)* `jacoco-maven-plugin` | Relatório de cobertura (sem gate numérico nesta fatia) |
| *(plugin)* `maven-failsafe-plugin` | Separa testes de integração (`*IT`) dos unitários (`*Test`) |

**Deliberadamente fora:** Lombok (DTOs são `record`; entidades JPA com código explícito — `@Data` e JPA se dão mal em `equals/hashCode`), MapStruct (mapeamento manual, o volume é pequeno), QueryDSL (a busca é SQL nativo — ver DT-07), Elasticsearch, Redis, message broker.

### DT-03 — Identificadores
UUID v7 gerado na aplicação (`Generators.timeBasedEpochGenerator()`), exposto num `IdGenerator` em `shared/id`. Coluna `uuid`, `@Id` atribuído antes do `persist` (sem `@GeneratedValue`), o que mantém as entidades previsíveis e evita `SELECT` de retorno.

### DT-04 — Enums
`varchar` + `CHECK` no banco, `@Enumerated(EnumType.STRING)` no Java.
*Alternativa descartada:* tipo `ENUM` nativo do Postgres — exige cast explícito no Hibernate e um `ALTER TYPE` a cada valor novo, sem ganho real.

### DT-05 — Arrays e JSON
`text[]` (`supported_languages`, `pros`, `cons`) via `@JdbcTypeCode(SqlTypes.ARRAY)`; `faq jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`. Suporte nativo do Hibernate 6 — sem `hibernate-types`.
Os limites de tamanho (0–8 itens, ≤ 200 chars etc.) são validados na borda com Bean Validation, não no banco.

### DT-06 — Materialização do `tsvector`: **trigger**, não coluna gerada
A spec §4.6 manda incluir nomes de tags e integrações no vetor (peso D). Coluna `GENERATED ALWAYS` só enxerga a própria linha, então não serve. Solução:

- Função `catalog_refresh_product_search_vector(p_product_id uuid)` recalcula `search_vector_pt` e `search_vector_en` de um produto.
- Triggers que a chamam:
  - `product` — `AFTER INSERT OR UPDATE OF name, tagline_pt, tagline_en, description_pt, description_en`
  - `product_tag`, `product_integration` — `AFTER INSERT OR DELETE`
  - `tag`, `integration` — `AFTER UPDATE OF name/name_pt/name_en` (recalcula os produtos ligados)
- Pesos: `A` = `name`, `B` = `tagline_<locale>`, `C` = `description_<locale>`, `D` = tags + integrações.
- `unaccent()` é aplicado **dentro** da função. Como a coluna é comum (materializada pelo trigger) e não gerada, a imutabilidade de `unaccent` não é exigida — que é justamente o que impediria a coluna gerada.

*Alternativa descartada:* criar text search configurations próprias (`pt_unaccent`, `en_unaccent`) para tornar a expressão imutável. Só faria sentido com coluna gerada, que já foi descartada acima. Fica registrada caso o modelo mude.

Isso atende **AC-22** (edição no admin reflete na busca) sem código de aplicação: a consistência é do banco.

### DT-07 — Motor de busca: SQL nativo isolado no módulo `search`
`ProductSearchJdbcRepository` com `NamedParameterJdbcTemplate`. Motivo: filtros dinâmicos combináveis, `ts_rank_cd`, fallback trigram e `count(*) OVER ()` não cabem em JPQL/Criteria sem virar um gerador de string pior que o SQL. JPA fica com o CRUD (catalog/admin).

- `q` → `websearch_to_tsquery('portuguese'|'english', unaccent(:q))` — tolera aspas, `or`, `-` e nunca lança erro de sintaxe com entrada de usuário (ao contrário de `to_tsquery`).
- Ordenação com `q`: `ts_rank_cd(search_vector, query, 32) DESC, updated_at DESC` — o flag 32 normaliza para `rank/(rank+1)`, deixando o score comparável entre resultados.
- Sem `q`: `updated_at DESC` (spec §5.1).
- **Fallback trigram (AC-12):** executado **apenas** quando o FTS devolve 0 linhas — segunda query com `similarity(unaccent(name), unaccent(:q)) > 0.3 ORDER BY similarity DESC`. Uso de `similarity()` explícito em vez do operador `%`, que depende do GUC `pg_trgm.similarity_threshold` e tornaria o comportamento dependente de sessão. Consequência aceita (T17): o índice `product_name_trgm_idx` não é usado por esse predicado — só o operador `%` o usaria. Tolerável porque o fallback só roda depois de o FTS já ter devolvido zero linhas.
- `total` obtido com `count(*) OVER ()` na mesma query (uma ida ao banco).
- Filtros: `category` resolvido para o conjunto {categoria + filhas} antes da query; `integration` repetível vira `AND` por `EXISTS` encadeado; `pricing_model` repetível vira `IN`; `price_max` compara `price_min <= :price_max AND price_min IS NOT NULL` (AC-14).

### DT-08 — Resolução de idioma
`LocaleResolutionFilter` (`OncePerRequestFilter`) rodando antes dos controllers:

1. `?lang` presente → valida contra `{pt-BR, en}`; inválido → **400** problem+json (AC-09).
2. Senão, `Accept-Language` parseado com q-values; primeiro idioma suportado vence.
3. Senão, `APP_DEFAULT_LOCALE` (default `pt-BR`).

Resultado vai para um atributo da requisição, lido pelo `RequestLocale`, e o filtro escreve `Content-Language` na resposta. `?lang` sempre vence o header (AC-08).

**Corrigido no T12:** a tradução das mensagens passa a usar o idioma resolvido pelo filtro, não o `LocaleContextHolder`. O `RequestContextFilter` do Spring sobrescreve o holder com `request.getLocale()`, que só enxerga `Accept-Language` — o 401 saía em inglês numa requisição `?lang=pt-BR`, com o `Content-Language` correto no cabeçalho e o corpo no idioma errado.

**Corrigido no T11:** um bean `LocaleResolver` chamado `localeResolver` é obrigatório. Sem ele o `DispatcherServlet` sobrescreve o `LocaleContextHolder` no início de cada requisição com o resultado do resolvedor padrão, que só olha `Accept-Language` — o filtro acertava e o framework desfazia. O `RequestLocale` também deixou de ser bean com escopo de requisição: guardar num proxy dentro de um filtro singleton não traria ganho sobre o atributo da requisição.
Enum `AppLocale { PT_BR("pt-BR", "portuguese"), EN("en", "english") }` carrega também o dicionário FTS, evitando `if` espalhado.

### DT-09 — Erros (RFC 9457)
`ProblemDetail` do Spring 6 + um `@RestControllerAdvice` global em `shared/error`. `type` = `https://saasfinder.com.br/problems/<slug>`. Extensão `errors[]` (`{field, code, message}`) para falhas de validação e para o 422 de publicação. `title`/`detail` localizados via `MessageSource` (`messages_pt_BR.properties`, `messages_en.properties`) no locale resolvido.
Nenhum `try/catch` em controller: exceções de domínio (`ProductNotFoundException`, `SlugImmutableException`, `CategoryNotEmptyException`, `ProductNotPublishableException`) são traduzidas num lugar só.

### DT-10 — Segurança
`SecurityFilterChain` stateless, CSRF desabilitado (API sem cookie), `/admin/**` exige `ROLE_ADMIN` via `httpBasic`, todo o resto `permitAll`. Usuário único em `InMemoryUserDetailsManager` a partir de `ADMIN_USER`/`ADMIN_PASSWORD` (senha codificada com BCrypt no boot; a app falha ao subir se as variáveis não estiverem setadas — sem default embutido).
`AuthenticationEntryPoint` custom devolve **401** em `application/problem+json` (AC-17), não o `WWW-Authenticate` cru com HTML.
Actuator: só `health` exposto (`management.endpoints.web.exposure.include=health`); `/env` e `/configprops` ficam fora — apoia AC-24.

### DT-11 — Logs
`logback-spring.xml` com `LogstashEncoder` no profile default; console legível no profile `local`. O boot não loga propriedades de ambiente (nada de `--debug` nem `logging.level` de config em DEBUG). O teste de AC-24 captura a saída do boot e falha se o valor de `ADMIN_PASSWORD` ou `DB_PASSWORD` aparecer.

### DT-12 — Paginação e envelope
Offset (`page` default 0, `size` default 20, máximo 50 → **400** acima disso, AC-15). Envelope próprio `PageResponse<T> { items, page, size, total, query }` — não serializar `Page` do Spring, cuja estrutura JSON é instável entre versões e vaza `pageable`/`sort`.
`query` é o eco dos filtros efetivamente aplicados (spec §5.1), útil para o frontend renderizar chips e para depurar.

### DT-13 — N+1
A listagem monta os produtos e depois busca integrações/tags em lote (`WHERE product_id IN (...)`) num segundo `SELECT`. Nunca acesso lazy dentro de laço. A ficha de produto usa uma query para o agregado (produto + categoria + tags + integrações, com `join fetch`) e uma segunda para o `product_content` do locale pedido — **precisado no T18**: `product_content` não é coleção mapeada de `Product`, porque a ficha quer um idioma só.

### DT-14 — Testes
- Base `AbstractIntegrationTest` com container `pgvector/pgvector:pg16` `static` (uma instância reutilizada por toda a suíte) e `@ServiceConnection`. Flyway roda de verdade em cada boot.
- HTTP via **MockMvc** (já vem no starter; RestAssured seria dependência a mais sem ganho — a spec permite qualquer um dos dois).
- `SEED_ENABLED=false` nos testes; dados vêm de fixtures/builders explícitos, para os asserts não dependerem do conteúdo editorial do seed. **Exceção:** um `SeedIT` que roda com `SEED_ENABLED=true` e valida AC-02 (idempotência) e a integridade do arquivo.
- Convenção: `*Test` = unitário (surefire), `*IT` = integração (failsafe). Regra da constituição §6: todo endpoint tem ao menos um IT de caminho feliz e um de erro.
- **Ajuste do T04:** a propriedade `api.version` é fixada em `1.40` no `pom.xml` para surefire e failsafe. O docker-java usado pelo Testcontainers negocia a API do Docker em `1.32` por padrão, e o Docker 29 recusa qualquer coisa abaixo de `1.40`. O valor `1.40` existe desde o Docker 19.03 e é aceito pelos daemons novos, então serve para qualquer máquina; sobrescrever com `-Ddocker.api.version=` se preciso.

### DT-15 — Seed
`SeedRunner` (`ApplicationRunner`) ativo somente com `SEED_ENABLED=true`. Idempotência (AC-02) por duas travas: `pg_advisory_xact_lock` no início da transação (protege execução concorrente de réplicas) e checagem de banco vazio (protege restart). **Corrigido no T25, depois de o CI quebrar:** a checagem cobre **tudo o que o seed escreve** — `category`, `product`, `integration` e `tag` —, e não só as duas primeiras. Um banco com integrações gravadas e nenhuma categoria passava por vazio, e aí o seed tentava recriar a mesma integração e derrubava o boot com 409. Meia carga anterior é justamente o caso em que reaplicar o seed não funciona.
Arquivos em `src/main/resources/seed/`:

```
seed/
├─ categories.yml       # 1 raiz + 4 subcategorias, pt e en
├─ integrations.yml     # ~12
├─ tags.yml             # ~15
└─ products/
   ├─ <slug>.yml        # 1 arquivo por produto — diff legível na revisão
   └─ ...
```

O loader reaproveita o **mesmo** validador de publicação do admin (DT-16). Seed inválido derruba o boot em dev, que é onde o erro custa menos.

### DT-16 — Regra de publicação num só lugar
`ProductPublicationValidator` (em `catalog/domain`) devolve a lista de pendências: conteúdo presente nos dois locales, ≥ 1 `supported_language`, coerência preço × `pricing_model`, campos obrigatórios preenchidos. Usado por `POST /admin/products/{id}/publish` (422 com `errors[]`, AC-19) e pelo seed. Zero duplicação de regra.

### DT-17 — JSON em `snake_case`
`PropertyNamingStrategies.SNAKE_CASE` global, como a spec §5.1 já escreve os campos (`logo_url`, `price_min`, `has_free_trial`). Vale para request e response, público e admin. Query params também em `snake_case` (`price_max`, `has_free_trial`), consistente com o corpo.

### DT-18 — Docker
`Dockerfile` multi-stage: `maven:3.9-eclipse-temurin-21` (com cache de `~/.m2` via layer de `dependency:go-offline`) → `eclipse-temurin:21-jre-jammy`, usuário não-root criado no runtime stage, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.
`docker-compose.yml`: serviço `db` com `pgvector/pgvector:pg16`, healthcheck `pg_isready`, volume nomeado `pgdata`; serviço `app` com `depends_on: db: condition: service_healthy`. `.env.example` versionado, `.env` no `.gitignore`.

**Corrigido no T03:** o `app` ficou **sem** healthcheck. A spec §7 só pede healthcheck no `db`, e a imagem `21-jre-jammy` não traz `curl` nem `wget` — instalar pacote só para isso não se paga, e a verificação do AC-01 é feita do host. Um `.dockerignore` também entrou: sem ele o `target/` e o `.git` subiam para o daemon a cada build.

---

## 2. Estrutura de pacotes

```
marketplace-saas/
├─ pom.xml
├─ mvnw · mvnw.cmd · .mvn/
├─ Dockerfile
├─ docker-compose.yml
├─ .env.example
├─ README.md
├─ CLAUDE.md
├─ specs/001-catalogo-core/{spec.md,plan.md,tasks.md}
└─ src/
   ├─ main/java/br/com/saasfinder/
   │  ├─ SaasFinderApplication.java
   │  ├─ shared/
   │  │  ├─ config/     SecurityConfig · OpenApiConfig · JacksonConfig · WebConfig
   │  │  ├─ locale/     AppLocale · RequestLocale · LocaleResolutionFilter
   │  │  ├─ error/      GlobalExceptionHandler · ProblemTypes · ApiException (+ subtipos) · ApiFieldError
   │  │  ├─ id/         IdGenerator (UUID v7)
   │  │  └─ web/        PageResponse · PagingParams · validadores de query
   │  ├─ catalog/
   │  │  ├─ api/        CategoryController · ProductController · IntegrationController · dto/
   │  │  ├─ domain/     Category · Product · ProductContent · Integration · Tag
   │  │  │             PricingModel · PricePeriod · ProductStatus
   │  │  │             ProductPublicationValidator · CatalogFacade · CatalogWriteService
   │  │  └─ infra/      *Repository (JPA) · seed/SeedRunner · seed/dto
   │  ├─ search/
   │  │  ├─ domain/     ProductSearchQuery · SearchResult · SearchFacade
   │  │  └─ infra/      ProductSearchJdbcRepository · SearchSqlBuilder · RowMappers
   │  └─ admin/
   │     └─ api/        AdminCategoryController · AdminProductController
   │                   AdminIntegrationController · AdminTagController · dto/
   ├─ main/resources/
   │  ├─ application.yml · application-local.yml · application-test.yml
   │  ├─ logback-spring.xml
   │  ├─ messages_pt_BR.properties · messages_en.properties
   │  ├─ db/migration/V1__init.sql
   │  └─ seed/…
   └─ test/java/br/com/saasfinder/
      ├─ support/      AbstractIntegrationTest · PostgresContainer · fixtures/
      ├─ catalog/      CategoryControllerIT · ProductControllerIT · SeedIT · …
      ├─ search/       ProductSearchIT · SearchSqlBuilderTest
      └─ admin/        AdminProductControllerIT · PublishFlowIT · SecurityIT
```

### Fronteiras entre módulos (constituição §4)

Nenhum módulo toca repositório de outro. A conversa acontece por facade:

| Facade | Módulo dono | Contrato |
|---|---|---|
| `CatalogFacade` | `catalog` | `Optional<ProductView> findPublishedBySlug(String slug, AppLocale locale)` · `List<ProductView> findPublishedBySlugs(List<String> slugs, AppLocale locale)` · `List<CategoryNodeView> categoryTree(AppLocale locale)` · `Set<UUID> categoryAndDescendantIds(String slug)` |
| `SearchFacade` | `search` | `SearchResult<ProductSummaryView> search(ProductSearchQuery query)` |

`ProductController` (em `catalog/api`) atende tanto `GET /products` quanto `GET /products/{slug}`: rota pública única, delegando a consulta ao `SearchFacade`. O módulo `search` não expõe controller próprio — ele é motor, não superfície.

`admin` depende de `catalog` (facade + serviço de escrita) e de nada mais. **Precisado no T21:** o serviço de escrita (`CatalogWriteService`) mora no `catalog`, não no `admin` — a constituição §4 proíbe um módulo de tocar o repositório de outro, e as invariantes (profundidade, unicidade de slug, guardas de exclusão) são do catálogo, não da política de admin. Os controllers de `admin` ficam finos: validam o corpo na borda e delegam.

**Sem eventos nesta fatia.** A recalculação do `tsvector` é do banco (DT-06), então um `ProductChangedEvent` não teria consumidor. Criar agora violaria a constituição §7.2.

---

## 3. Migração `V1__init.sql` — o que ela cria

**Extensões:** `unaccent`, `pg_trgm`, `vector` (esta última habilitada e não usada, conforme spec §2).

**Tabelas:** `category`, `category_slug` (ver D-01), `product`, `product_content`, `integration`, `product_integration`, `tag`, `product_tag` — colunas exatamente como a spec §4.

**Constraints de banco (só as que valem para qualquer estado, inclusive DRAFT):**

| Constraint | Regra |
|---|---|
| `CHECK` de domínio | `status`, `pricing_model`, `price_period`, `locale` restritos aos valores do enum |
| `CHECK` de moeda | `price_currency IS NOT NULL` quando `price_min IS NOT NULL` |
| `CHECK` de tamanho | limites de `name`, `tagline_*`, `description_*`, `slug` |
| `FK` | `product.category_id` → `category` (RESTRICT), `product_content.product_id` → `product` (CASCADE), N:N com CASCADE |
| Trigger `category_depth_guard` | rejeita categoria cujo pai já tem pai (profundidade máxima 2, spec §4.1) |

A regra "`price_min` obrigatório se `pricing_model ∈ {SUBSCRIPTION, USAGE_BASED, ONE_TIME}`" **não** vira `CHECK`: ela impediria criar rascunho incompleto, que é exatamente o fluxo do curador. Vira regra de publicação (DT-16, D-04).

**Corrigido no T26 (migração `V6`):** `price_min` passou de `numeric(12,2)` para `numeric(12,4)`. Com duas casas, um produto `USAGE_BASED` cobrado a US$ 0,005 por mensagem virava US$ 0,01 — o dobro —, e como a regra acima exige `price_min` para esse modelo, a alternativa seria gravar preço errado ou nunca publicar o produto. Alargar a coluna não invalida nada já gravado.

**Índices:**

| Tabela | Índice |
|---|---|
| `category` | `UNIQUE(slug_pt)` · `UNIQUE(slug_en)` · `(parent_id, sort_order)` |
| `category_slug` | `PK(slug)` · `(category_id)` |
| `product` | `UNIQUE(slug)` · `(category_id)` · `(status)` · `GIN(search_vector_pt)` · `GIN(search_vector_en)` · `GIN(name gin_trgm_ops)` · `(price_min) WHERE price_min IS NOT NULL` |
| `product_content` | `PK(product_id, locale)` |
| `product_integration` / `product_tag` | `PK(product_id, x_id)` + índice na coluna inversa |

**Funções/triggers:** `catalog_refresh_product_search_vector(uuid)` + os cinco triggers de DT-06; `category_depth_guard`; `category_slug_sync` (mantém a tabela de lookup em INSERT/UPDATE de `category`).

Migração é imutável depois de commitada (constituição §5). Correção = `V2__…`.

---

## 4. Contratos — API pública

Convenções: base `/api/v1`; JSON `snake_case`; toda resposta com `Content-Language`; datas ISO-8601 UTC; erro sempre `application/problem+json`.

### 4.1 `GET /categories`

```json
{
  "locale": "pt-BR",
  "items": [
    {
      "slug": "ia-e-automacao-para-pmes",
      "name": "IA e automação para PMEs",
      "description": "…",
      "sort_order": 0,
      "product_count": 20,
      "children": [
        { "slug": "atendimento-e-chatbots", "name": "Atendimento e chatbots",
          "description": "…", "sort_order": 0, "product_count": 6, "children": [] }
      ]
    }
  ]
}
```

`product_count` conta produtos **publicados** da categoria **e de suas subcategorias** — ver D-02.

### 4.2 `GET /categories/{slug}`
Mesmo nó, sem envelope de lista. Aceita slug em pt ou en (AC-04) resolvido pela tabela `category_slug`. Slug desconhecido → 404.

### 4.3 `GET /products` (lista/busca)

**Ajustado no T15:** o envelope traz `locale` no corpo, além do header `Content-Language`, para ficar igual ao de `/categories` — rotas irmãs com formatos diferentes seriam ruído para quem consome.

```json
{
  "locale": "pt-BR",
  "items": [
    {
      "slug": "acme-chat",
      "name": "Acme Chat",
      "tagline": "Atendimento no WhatsApp com IA",
      "logo_url": "https://…",
      "category": { "slug": "atendimento-e-chatbots", "name": "Atendimento e chatbots" },
      "pricing_model": "SUBSCRIPTION",
      "price_min": 99.00,
      "price_currency": "BRL",
      "price_period": "MONTH",
      "has_free_trial": true,
      "has_free_plan": false,
      "integrations": [{ "slug": "whatsapp", "name": "WhatsApp" }]
    }
  ],
  "page": 0,
  "size": 20,
  "total": 37,
  "query": {
    "q": "atendimento whatsapp",
    "category": "atendimento-e-chatbots",
    "integration": ["whatsapp"],
    "pricing_model": [],
    "has_free_trial": true,
    "has_free_plan": null,
    "price_max": null,
    "matched_by": "FULL_TEXT"
  }
}
```

`matched_by` ∈ `FULL_TEXT` | `TRIGRAM` | `NONE` — torna o fallback de AC-12 observável e testável sem inspecionar o banco.

### 4.4 `GET /products/{slug}` (ficha)

```json
{
  "slug": "acme-chat",
  "locale": "pt-BR",
  "name": "Acme Chat",
  "tagline": "…",
  "description": "…",
  "logo_url": "https://…",
  "website_url": "https://…",
  "category": { "slug": "atendimento-e-chatbots", "name": "…" },
  "pricing_model": "SUBSCRIPTION",
  "price_min": 99.00,
  "price_currency": "BRL",
  "price_period": "MONTH",
  "has_free_trial": true,
  "has_free_plan": false,
  "supported_languages": ["pt", "en", "es"],
  "hq_country": "BR",
  "integrations": [{ "slug": "whatsapp", "name": "WhatsApp" }],
  "tags": [{ "slug": "chatbot", "name": "Chatbot" }],
  "pros": ["…"],
  "cons": ["…"],
  "best_for": "…",
  "faq": [{ "question": "…", "answer": "…" }],
  "published_at": "2026-08-20T13:04:11Z",
  "updated_at": "2026-08-24T09:00:00Z"
}
```

`source_note` **nunca** aparece aqui (spec §4.2) — só nos endpoints de admin.

### 4.5 `GET /products/compare?slugs=a,b,c`
`{ "locale": "...", "items": [ <ficha>, <ficha>, <ficha> ] }`, na ordem pedida. `< 2` ou `> 3` slugs → 400. Slug inexistente → 404 com extensão `missing_slugs: ["x"]` (AC-16).

### 4.6 `GET /integrations`
`{ "items": [ { "slug": "whatsapp", "name": "WhatsApp", "product_count": 9 } ] }` — contagem só de publicados.

### 4.7 Formato de erro (RFC 9457)

```json
{
  "type": "https://saasfinder.com.br/problems/validation-failed",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "instance": "/api/v1/admin/products",
  "errors": [
    { "field": "tagline_en", "code": "NotBlank", "message": "não pode estar em branco" }
  ]
}
```

| `type` slug | Status | Quando |
|---|---|---|
| `unsupported-locale` | 400 | `?lang` fora de {pt-BR, en} (AC-09) |
| `invalid-parameter` | 400 | `size > 50` (AC-15), `slugs` fora de 2–3 (AC-16), enum inválido |
| `validation-failed` | 400 | Bean Validation no corpo (AC-18) |
| `unauthorized` | 401 | `/admin/**` sem credencial ou com credencial errada (AC-17) |
| `not-found` | 404 | Slug/ID inexistente, ou produto não publicado em rota pública (AC-06) |
| `conflict` | 409 | Slug de produto PUBLISHED alterado (AC-20); categoria com filhas/produtos no DELETE (AC-21) |
| `not-publishable` | 422 | Publicação com pendências, listadas em `errors[]` (AC-19) |

---

## 5. Contratos — API admin (HTTP Basic)

Diferença estrutural: **admin fala nos dois idiomas ao mesmo tempo**, sem resolução por locale.

### 5.1 `POST /admin/products` · `PUT /admin/products/{id}`

```json
{
  "slug": "acme-chat",
  "name": "Acme Chat",
  "tagline_pt": "…", "tagline_en": "…",
  "description_pt": "…", "description_en": "…",
  "category_id": "0190…",
  "website_url": "https://…",
  "logo_url": "https://…",
  "pricing_model": "SUBSCRIPTION",
  "price_min": 99.00, "price_currency": "BRL", "price_period": "MONTH",
  "has_free_trial": true, "has_free_plan": false,
  "supported_languages": ["pt", "en"],
  "hq_country": "BR",
  "source_note": "https://acme.com/pricing — coletado em 2026-08-20",
  "integration_slugs": ["whatsapp", "hubspot"],
  "tag_slugs": ["chatbot", "ia-generativa"]
}
```

Resposta: representação administrativa completa (ambos os idiomas, `status`, `source_note`, `published_at`). `POST` cria sempre em `DRAFT`. `PUT` com `slug` diferente em produto `PUBLISHED` → 409 (AC-20).

### 5.2 `PUT /admin/products/{id}/content/{locale}`

```json
{
  "pros": ["…"],
  "cons": ["…"],
  "best_for": "…",
  "faq": [{ "question": "…", "answer": "…" }]
}
```

Semântica de **substituição** do registro daquele locale (spec §5.2).

### 5.3 Transições de status
`POST …/publish` → 200 com o produto, `published_at` setado na primeira vez; 422 com `errors[]` listando pendências (AC-19).
`POST …/unpublish` → `PUBLISHED → DRAFT`. `POST …/archive` → qualquer → `ARCHIVED`. Transição inválida → 409.

### 5.4 Categorias, integrações e tags
`GET/POST /admin/categories`, `PUT /admin/categories/{id}`, `DELETE /admin/categories/{id}` (409 se tiver filhas ou produtos, AC-21). `GET/POST /admin/integrations` e `/admin/tags`: CRUD simples, corpo com `slug` + nomes.
`GET /admin/products?status=DRAFT|PUBLISHED|ARCHIVED` — sem filtro, retorna todos os status, paginado no mesmo envelope de DT-12.

---

## 6. Rastreabilidade — critério de aceite → onde vive

| AC | Implementação | Verificação |
|---|---|---|
| AC-01 | `Dockerfile`, `docker-compose.yml`, actuator | Manual, passo no `README` |
| AC-02 | `SeedRunner` (advisory lock + contagem) | `SeedIT` |
| AC-03 | `CategoryController` + agregação de contagem | `CategoryControllerIT` |
| AC-04 | Tabela `category_slug` | `CategoryControllerIT` |
| AC-05 | `ProductSearchJdbcRepository` (`status = 'PUBLISHED'`) | `ProductSearchIT` |
| AC-06 | Filtro de status na busca por slug | `ProductControllerIT` |
| AC-07 | Projeção por locale nos DTOs | `ProductControllerIT` |
| AC-08 | `LocaleResolutionFilter` | `LocaleResolutionIT` |
| AC-09 | Idem | `LocaleResolutionIT` |
| AC-10 | Trigger de `tsvector` + `websearch_to_tsquery` | `ProductSearchIT` (fixtures bilíngues) |
| AC-11 | `unaccent` no trigger e na query | `ProductSearchIT` |
| AC-12 | Fallback trigram + `matched_by` | `ProductSearchIT` |
| AC-13 | `SearchSqlBuilder` | `ProductSearchIT` + `SearchSqlBuilderTest` |
| AC-14 | Filtro `price_max` | `ProductSearchIT` |
| AC-15 | `PagingParams` (validação) | `ProductControllerIT` |
| AC-16 | `ProductController#compare` | `CompareIT` |
| AC-17 | `SecurityConfig` + entry point | `SecurityIT` |
| AC-18 | Bean Validation + advice | `AdminProductControllerIT` |
| AC-19 | `ProductPublicationValidator` | `PublishFlowIT` |
| AC-20 | `CatalogWriteService` (guarda de slug) | `AdminProductControllerIT` |
| AC-21 | `CatalogWriteService` (guarda de exclusão) | `AdminCategoryControllerIT` |
| AC-22 | Triggers de DT-06 | `AdminProductControllerIT` (edita → busca) |
| AC-23 | Anotações springdoc + `OpenApiConfig` | `OpenApiIT` (bate rotas do `/v3/api-docs` contra as registradas) |
| AC-24 | `logback-spring.xml` + actuator restrito | `LogRedactionIT` |

---

## 7. Riscos e pontos de atenção

1. **Dicionário FTS é fixo por coluna.** Produto brasileiro com termos em inglês na descrição pt é indexado com stemmer português. Aceitável nesta escala; se virar problema, a saída é uma terceira coluna `simple`, não trocar de motor.
2. **Fallback trigram cobre só `name`** (spec §4.6). Typo em tagline ou descrição não é tolerado. Explícito, não esquecido.
3. **Testcontainers exige Docker no CI.** Sem daemon, a suíte não roda — precisa constar no `README` e na config de pipeline.
4. **Seed com dados reais envelhece.** `source_note` carrega a data de coleta; a spec de ranking futura provavelmente precisará de uma rotina de revalidação.
5. **AC-10 depende do conteúdo editorial.** Por isso os testes de busca usam fixtures controladas; o seed real entra só no teste de fumaça.
6. **`ts_rank_cd` não é comparável entre queries diferentes** — só ordena dentro de um resultado. Nada de expor "score de relevância" ao cliente.
7. **Extensões exigem privilégio.** A imagem `pgvector/pgvector:pg16` sobe com superusuário e o `CREATE EXTENSION` do V1 funciona. Em Postgres gerenciado, isso pode precisar de habilitação prévia — anotar no `README`.

---

## 8. Desvios propostos à spec (precisam de aprovação antes de virar tarefa)

Constituição §2.5: spec é fonte da verdade; se o plano diverge, a spec é atualizada **antes** do código.

| # | Desvio | Motivo |
|---|---|---|
| **D-01** | Nova tabela `category_slug (slug PK, category_id, locale)`, mantida por trigger | A spec quer `/categories/{slug}` aceitando slug em qualquer idioma (AC-04), mas só garante unicidade **dentro** de cada coluna. Nada impede o `slug_pt` de uma categoria colidir com o `slug_en` de outra, e aí a rota fica ambígua. A tabela de lookup garante unicidade global e resolve a rota numa query só |
| **D-02** | `product_count` inclui subcategorias | O filtro `category=X` já inclui subcategorias (spec §5.1). Contagem que não incluísse mostraria 0 na categoria raiz e um número diferente do que a busca devolve |
| **D-03** | `Accept-Language` com idioma não suportado → cai no default; só `?lang` explícito inválido dá 400 | Negociação de conteúdo HTTP prevê fallback. Um navegador mandando `fr-FR,fr;q=0.9` não deve receber 400 numa API pública. A spec §5 diz "inválido → 400" sem distinguir os dois casos |
| **D-04** | Coerência `price_min` × `pricing_model` validada na publicação, não como `CHECK` | Um `CHECK` impediria salvar rascunho incompleto — que é o fluxo normal do curador (spec §3) |
| **D-05** | `q` usa `websearch_to_tsquery` | A spec não define o parser. `to_tsquery` estoura com entrada livre do usuário; `plainto_tsquery` ignora aspas e negação |
| **D-06** | Campo `matched_by` na resposta de `GET /products` | Torna o fallback trigram (AC-12) verificável pela API, em vez de por inspeção de banco |
| **D-07** | JSON e query params em `snake_case` | Confirma explicitamente o que a spec já escreve nos campos; evita divergência na implementação |

Aprovados os desvios, atualizo `spec.md` (§4, §5, §10) antes de gerar `tasks.md`.

---

## 9. Fora deste plano (confirmando a spec §2)

Frontend, reviews, ranking, assistente/embeddings, autenticação de usuário final, upload de imagem, entidade `vendor`, paginação por cursor, cache, rate limiting, observabilidade além do `health`.

---

## 10. Esboço de `tasks.md` (ordem sugerida, uma tarefa por commit)

| # | Tarefa | Entrega verificável |
|---|---|---|
| T01 | Esqueleto Maven + `SaasFinderApplication` + `application.yml` | `mvn verify` passa; app sobe |
| T02 | `Dockerfile`, `docker-compose.yml`, `.env.example`, actuator health | AC-01 manual |
| T03 | `AbstractIntegrationTest` + Testcontainers | Um IT trivial passa contra Postgres real |
| T04 | `V1__init.sql`: extensões, tabelas, enums, FKs, índices | Migração aplica do zero |
| T05 | `V1`: triggers de `tsvector`, `category_depth_guard`, `category_slug_sync` | IT de SQL puro valida recálculo |
| T06 | Entidades JPA + repositórios + `IdGenerator` | ITs de persistência |
| T07 | `shared/error` (advice, ProblemDetail, mensagens pt/en) | IT de 404 e 400 |
| T08 | `shared/locale` (filtro, `AppLocale`, `Content-Language`) | AC-08, AC-09 |
| T09 | `SecurityConfig` + entry point 401 | AC-17 |
| T10 | `GET /categories` e `/categories/{slug}` | AC-03, AC-04 |
| T11 | `search`: `SearchSqlBuilder` + repositório (FTS, filtros, paginação) | AC-05, AC-13, AC-14, AC-15 |
| T12 | Fallback trigram + `matched_by` | AC-10, AC-11, AC-12 |
| T13 | `GET /products/{slug}` (ficha resolvida por locale) | AC-06, AC-07 |
| T14 | `GET /products/compare` | AC-16 |
| T15 | `GET /integrations` | Endpoint com contagem |
| T16 | Admin: CRUD de categorias, integrações, tags | AC-21 |
| T17 | Admin: CRUD de produtos + guarda de slug | AC-18, AC-20, AC-22 |
| T18 | Admin: conteúdo por locale + `ProductPublicationValidator` + publish/unpublish/archive | AC-19 |
| T19 | `SeedRunner` + arquivos de seed (estrutura + 4 produtos) | AC-02 |
| T20 | Os 20 produtos reais do seed (pesquisa + revisão do Wesley) | Spec §6 |
| T21 | Swagger: anotações, exemplos, `OpenApiConfig` | AC-23 |
| T22 | Logs JSON + verificação de redação | AC-24 |
| T23 | `README` (rodar, testar, entregas da fatia) + fechamento do DoD | Constituição §8 |
