# CONSTITUTION — Marketplace bilíngue de SaaS

> Este arquivo define as regras invariantes do projeto. Toda spec em `specs/` herda estas regras.
> Copie o conteúdo para `CLAUDE.md` na raiz do repositório.

## 1. O que estamos construindo

Um diretório/marketplace bilíngue (pt-BR / en) de soluções SaaS, com rankings transparentes e um assistente de IA restrito ao catálogo. Fase 1 é **single-sided**: catálogo curado, busca, reviews e assistente. Sem pagamentos, sem KYB, sem enterprise.

**A plataforma é vitrine** (decisão de 2026-08-27, spec 003 §10). Ela mostra o máximo que conseguir sobre cada SaaS e **redireciona** para o site do fornecedor. Nada que envolva vender — `vendor` como entidade, claim de listagem, captura de lead, "quero uma demo", newsletter, plano pago, repasse — entra sem revisão jurídica antes: cada um deles cria obrigação contratual ou responsabilidade sobre terceiro. Isso reforça o single-sided do plano de produto §2.1, por um motivo diferente do dele.

## 2. Processo (Spec-Driven Development)

1. Toda funcionalidade nasce em `specs/NNN-nome/spec.md` (o quê e por quê, critérios de aceite).
2. A partir da spec, gerar `plan.md` (como: decisões técnicas, estrutura de arquivos, contratos) e `tasks.md` (lista ordenada de tarefas pequenas, cada uma verificável).
3. Só então escrever código. Uma tarefa por commit. Commit message referencia a tarefa (`[001-T03] ...`).
4. Nada é "pronto" sem: testes passando, `docker compose up` funcionando do zero, critérios de aceite da spec verificados um a um.
5. Quando a implementação revelar que a spec estava errada, **atualizar a spec primeiro**, depois o código. A spec é a fonte da verdade.
6. Ambiguidade na spec → perguntar, não assumir. Marcar dúvidas com `[NEEDS CLARIFICATION: ...]` na spec.

**Promoção do código:** `feature/*` → PR automático para `dev` → `homol` → `main`.

- `dev` recebe as fatias em andamento. Todo push roda a **smoke**.
- `homol` é o candidato. Promover para lá **dispara a suíte completa de ponta a ponta** — é o gatilho, não um passo manual a lembrar.
- `main` só recebe o que passou pela `homol`. Como o check da suíte completa fica ancorado no SHA, ele já aparece verde no PR de release quando a promoção é fast-forward.

> **O gate é convenção, não mecanismo.** O repositório é privado num plano Free, e nesse plano **proteção de branch e rulesets não existem** — a API responde `403 — Upgrade to GitHub Pro or make this repository public`. Não há required check, e um PR com CI vermelho pode ser mergeado (`mergeable: true`, `mergeable_state: unstable`). Verificado em 2026-08-27. Enquanto for assim, "não mergear vermelho" e "promover só o que passou" são disciplina de quem aperta o botão. Não escreva "required check" nas specs como se houvesse enforcement.

## 3. Stack (fixa — não substituir sem atualizar este arquivo)

| Camada | Escolha |
|---|---|
| Linguagem / framework | Java 21, Spring Boot 3.x, Maven |
| Persistência | PostgreSQL 16 com extensões `unaccent`, `pg_trgm`, `vector` (pgvector). Flyway para migrações. Spring Data JPA. |
| Busca | Postgres full-text search com dicionários `portuguese` e `english`. Sem Elasticsearch/OpenSearch. |
| API | REST + JSON, documentada com springdoc-openapi (Swagger UI em `/swagger-ui.html`). |
| Testes | JUnit 5, Testcontainers (Postgres real), RestAssured ou MockMvc. Sem H2. |
| Containers | Docker multi-stage build; `docker-compose.yml` na raiz sobe app + Postgres. |
| Frontend | Next.js (App Router, **SSR**) em `web/`, TypeScript estrito, Tailwind, shadcn/ui sobre Radix. i18n por rota, com prefixo obrigatório nos dois idiomas. |
| Testes do frontend | Vitest + Testing Library (unidade e componente), Playwright (fluxo, inclusive com JavaScript desabilitado), axe (acessibilidade). |
| IA (fase posterior) | Camada de abstração própria (`LlmClient`, `EmbeddingClient`) com adapters por provedor. |

## 4. Arquitetura

- **Monolito modular.** Um único artefato Spring Boot. Pacote raiz `br.com.<nome>.` com um subpacote por módulo:
  `catalog`, `search`, `reviews`, `ranking`, `assistant`, `identity`, `admin`, `shared`.
- Cada módulo expõe apenas o que outros módulos precisam via uma classe `*Facade` ou eventos internos (`ApplicationEventPublisher`). Nenhum módulo acessa repositório de outro diretamente.
- Estrutura interna de módulo: `api/` (controllers, DTOs), `domain/` (entidades, regras), `infra/` (repositórios, adapters).
- Sem microsserviços, sem message broker, sem cache distribuído até uma spec justificar.

**Frontend** (`web/`, mesmo repositório — monorepo):

- Um segundo artefato, não um segundo repositório: uma constituição, um `specs/`, um `docker compose up --build`, e mudança de contrato entrando num commit só.
- Renderização no servidor. **Não é SPA:** a primeira resposta é HTML, porque o consumidor principal do catálogo é buscador.
- Consome **apenas** a API pública `/api/v1/**`, e a partir do servidor. Não fala com o banco, não usa `/admin/**`, não tem BFF próprio nem cache além do que o framework faz por padrão.
- O contrato entre os dois lados é o documento OpenAPI: os tipos do frontend são **gerados** dele, nunca escritos à mão. Não existe código compartilhado entre Java e TypeScript.
- O estado de navegação vive na **URL** (filtro, página, comparação): compartilhável e sobrevive ao botão "voltar". Sem gerenciador de estado global.

## 5. Regras de dados

- **Texto de interface e conteúdo curado existem em pt e en.** Rótulo, mensagem, texto de erro, corpo de e-mail que nós enviamos, e todo dado de catálogo escrito pela curadoria. Padrão: colunas `campo_pt` / `campo_en`, ou tabela `*_translation (entity_id, locale, ...)` quando o conteúdo for longo. Nunca campo único.
- **Conteúdo gerado por usuário existe num idioma só** — o de quem escreveu. Ele carrega o próprio `locale` na coluna do registro, aparece na tela **marcado** quando o idioma difere do da página, e **nunca é traduzido automaticamente** (§7). A regra bilíngue acima vale para o que **nós** escrevemos; ela não se aplica ao que escrevem **para** nós. Emenda da spec 003 §4.3, que trouxe o primeiro conteúdo desse tipo (reviews).
- `locale` aceito: `pt-BR` e `en`. Requisição indica via header `Accept-Language` ou query `?lang=`; default `pt-BR`.
- IDs: UUID v7 (ordenável). Slugs únicos e imutáveis após publicação.
- Toda tabela tem `created_at` e `updated_at` (timestamptz, UTC).
- Soft delete apenas onde a spec pedir; caso contrário, delete real com auditoria via evento.
- Migrações Flyway são imutáveis depois de commitadas. Correção = nova migração.

## 6. Regras de código

- Validação na borda (Bean Validation nos DTOs). Domínio recebe dados válidos.
- Erros seguem RFC 9457 (Problem Details): `application/problem+json` com `type`, `title`, `status`, `detail`, `errors[]`.
- Nada de lógica de negócio em controllers ou repositórios.
- Logs estruturados em JSON (logstash-logback-encoder). Nunca logar e-mail completo, tokens ou corpo de review.
- Configuração por variáveis de ambiente (`application.yml` referencia `${VAR:default}`). Segredos nunca no repositório.
- Cobertura mínima: todo endpoint tem ao menos um teste de integração feliz e um de erro.

**Testes de ponta a ponta** (valem para toda fatia com interface):

- O e2e existe **desde a primeira tarefa** da fatia, não numa fase no fim. Tela sem teste não fecha tarefa.
- `data-testid` é obrigatório em todo elemento que um teste precise alcançar. Em kebab-case, e **nunca** derivado de texto traduzível nem de dado de seed — a interface é bilíngue, então seletor por texto quebra ao trocar de idioma, e seletor por classe quebra ao mexer no estilo. Item de lista usa id de tipo mais um atributo que identifica qual.
- Nos testes, `getByTestId`. `getByText` só quando a asserção **é** sobre o texto.
- **A suíte roda em paralelo**, então todo teste é independente: ordem arbitrária, worker arbitrário, nenhum estado ou dado compartilhado. Proibidos `describe.serial`, estado de módulo mutável e depender da navegação deixada por outro teste.
- **Suíte de leitura não escreve.** Cenário que precise de outro estado de backend ganha outra instância no compose, nunca uma mutação no ambiente compartilhado. Onde houver escrita, cada teste cria o próprio dado com identificador único e não o reaproveita.
- Dois gatilhos, uma suíte só: **smoke** (recorte por tag, roda em todo push, orçamento curto e fixo) e **full** (sob demanda, obrigatória antes de release).

## 7. Nunca fazer

- Não adicionar dependência sem justificar no `plan.md` da spec.
- Não implementar nada que não esteja em uma spec aprovada ("já que estou aqui..." é proibido).
- Não mockar o banco em testes de integração.
- Não excluir teste com o intuito de fazer passar na pipe.
- Não colocar preço, moeda ou qualquer dado de produto "de cabeça" — seed vem de arquivo versionado com fonte indicada.
- Não traduzir automaticamente conteúdo escrito por usuário, nem editá-lo para "melhorar". Inventar preço é inventar dado de produto; traduzir uma review é inventar a opinião de uma pessoa identificável — é a mesma proibição, com o dano maior.
- Não escrever código que altere posição de ranking com base em pagamento. Regra de produto, não técnica.

## 8. Definição de pronto (DoD) — vale para toda spec

- [ ] `docker compose up --build` sobe tudo do zero e o endpoint de health responde.
- [ ] Todos os critérios de aceite da spec têm um teste automatizado ou um passo manual documentado no `README`.
- [ ] `mvn verify` passa (build + testes + análise estática configurada).
- [ ] Tocou o frontend: `npm run verify` passa em `web/`, a smoke e2e está verde, e cada tela nova entrou com `data-testid` e teste no mesmo commit.
- [ ] Swagger UI reflete todos os endpoints novos.
- [ ] `README.md` explica como rodar, testar e o que a fatia entrega.
- [ ] `spec.md` atualizada com qualquer desvio decidido durante a implementação.