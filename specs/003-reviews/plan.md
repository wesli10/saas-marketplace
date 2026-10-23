# Plano 003 — Reviews verificadas

| Campo | Valor |
|---|---|
| Spec | [`spec.md`](spec.md) |
| Constituição | [`CLAUDE.md`](../../CLAUDE.md) |
| Data | 2026-08-27 |

---

## 0. O que a spec já fechou (não reabrir aqui)

Plataforma é vitrine; sem conta de usuário; verificação por token de e-mail; moderação prévia; nota 1–5 mais "recomendaria?"; agregado calculado na consulta; review não se traduz; unicidade por e-mail; canal de contestação com decisão registrada. As seis perguntas foram fechadas na spec §11.

Este plano decide **como**.

## 1. Decisões técnicas

### DT-01 — Módulo `reviews`, com fronteira de verdade

Pacote `br.com.saasfinder.reviews`, com `api/`, `domain/` e `infra/`, como manda a constituição §4. Duas fronteiras:

- **`reviews` → `catalog`:** para saber se um slug existe e qual produto ele é, `reviews` chama o `CatalogFacade` — nunca o `ProductRepository`. É a regra que já vale entre `search` e `catalog`.
- **`catalog` → `reviews`:** a ficha e a lista de produtos precisam da nota. O caminho é um `ReviewFacade` com `notasDe(Collection<UUID>)`, devolvendo um mapa. **Uma chamada por página, nunca uma por produto** — é o que impede o N+1 do AC-14.

Sem evento nem cache entre os dois: a nota é lida junto da consulta que já acontece.

### DT-02 — Dependências

| Pacote | Para quê | Por que não sem ele |
|---|---|---|
| `spring-boot-starter-mail` | Envio SMTP | É o adapter de e-mail do Spring; escrever cliente SMTP à mão é reimplementar pior |
| `spring-boot-starter-validation` | Já está no projeto | Validação na borda (constituição §6) |
| **Mailpit** (imagem no compose, não é dependência Java) | Caixa de entrada local e do e2e | Sem ela, o fluxo principal da fatia só é testável à mão |

**Instalado na T03 (2026-08-27):** `spring-boot-starter-mail` sem versão própria — vem do BOM do `spring-boot-starter-parent` 3.5.3, e traz o `jakarta.mail` do Angus. A imagem é `axllent/mailpit:v1.21`, fixada como as outras do compose (`pgvector/pgvector:pg16`): `latest` faz a caixa de entrada mudar sozinha entre duas execuções do e2e.

**Nada para rate limit.** Uma janela deslizante em memória (`ConcurrentHashMap` com contadores por IP e limpeza por TTL) resolve o que a spec pede, e é ~40 linhas. Bucket4j ou Redis entrariam para sobreviver a múltiplas instâncias — e hoje **há uma instância só**. A limitação fica escrita no código e no README: se um dia houver duas, o limite dobra na prática, e aí é hora de trocar. Dependência que existe para um cenário que não existe é peso morto.

### DT-03 — `status` é máquina de estados, no domínio

`PENDING_EMAIL → PENDING_MODERATION → PUBLISHED | REJECTED`. As transições vivem em `Review`, não no serviço: `confirmar()`, `publicar()`, `rejeitar(motivo, quem)`. Transição inválida lança `TransicaoInvalidaException` → **409**, e não 400 — o pedido está bem formado; o estado é que não permite.

Nada de `boolean confirmado` + `boolean publicado`: dois booleanos permitem quatro estados, e um deles ("publicada sem confirmar") não deveria existir.

### DT-04 — Token de confirmação

- 32 bytes de `SecureRandom`, `Base64url` sem padding → o que vai no link.
- No banco, **só o SHA-256** (`token_hash`). Quem lê o banco não confirma review de terceiro; quem intercepta o e-mail já tem o link mesmo.
- TTL de 48 h, uso único (`used_at`), e uma linha por review (`review_id` único). Reenviar **substitui** o token: dois links válidos ao mesmo tempo dobram a janela de ataque sem ajudar ninguém.
- A busca é por hash, então há índice único em `token_hash`.

### DT-05 — E-mail: interface no domínio, envio depois do commit

```
domain/EnviadorDeEmail.java        interface, sem Spring
infra/mail/SmtpEnviadorDeEmail.java  JavaMailSender
infra/mail/Mensagens.java          textos pt-BR/en, um arquivo por idioma
```

- **O envio acontece depois do commit** (`@TransactionalEventListener(AFTER_COMMIT)`). Enviar dentro da transação faz uma falha de SMTP desfazer a review que já estava boa — e o AC-07 exige o contrário.
- Falha de envio é **logada e registrada**, não propagada: a review fica em `PENDING_EMAIL` e o reenvio funciona.
- Os textos são nossos, então são bilíngues (constituição §5). Sem engine de template: são duas frases e um link; `MessageFormat` basta e não traz Thymeleaf para o projeto.
- **Nunca logar o e-mail completo nem o token** (constituição §6). O log registra o domínio e o id da review.
- **O health indicator de e-mail fica desligado** (`management.health.mail.enabled: false`). O `starter-mail` liga um por conta própria, e ele abre conexão SMTP a cada `/actuator/health`: com o SMTP fora do ar o health vira 503, e derruba o healthcheck do compose, o `--wait` e o CI enquanto o catálogo inteiro continua respondendo. É o AC-07 num lugar que o plano não tinha previsto. Descoberto na T03, quando duas integrações do 001 ficaram vermelhas.

### DT-06 — Agregado: uma consulta, sem coluna denormalizada

```sql
select product_id,
       round(avg(rating)::numeric, 1)                          as media,
       count(*)                                                as total,
       count(*) filter (where would_recommend)::numeric / count(*) as recomenda,
       count(*) filter (where verified)                        as verificadas
  from review
 where status = 'PUBLISHED' and product_id in (:produtos)
 group by product_id
```

Uma consulta por página de produtos, alimentada pelos ids que a lista já tem. Produto sem review não aparece no resultado → `rating: null` (spec §4.4), e não zero.

O `count(*) filter` é padrão SQL e roda no Postgres desde a 9.4; a alternativa (`sum(case when …)`) diz a mesma coisa com mais ruído.

**Ajuste da T08 (2026-08-27):** a proporção de "recomendaria" sai **arredondada em duas casas**. A divisão crua devolve `0.66666666666666666667`, que vira ruído no JSON e no `types.gen.ts`; com `count` ao lado, o 004 recupera o número de "sim" exato a partir dos dois. O resto da consulta é o que este DT-06 já escrevia.

### DT-07 — Mudança de contrato no 001, e o que ela obriga

`ProductSummaryView` e `ProductDetailView` ganham `rating: { average, count, recommend_ratio, verified_count } | null`.

Isso é **emenda ao contrato do 001**, e arrasta três coisas no mesmo commit da tarefa que a fizer:
1. `specs/001-catalogo-core/spec.md` §10.1 com a emenda;
2. OpenAPI regenerado e `OpenApiFidelidadeIT` verde — foi ele que pegou dois defeitos de contrato na 002;
3. `web/src/lib/api/types.gen.ts` regenerado (`npm run api:types`), senão o CI acusa diff.

### DT-08 — Migração `V7__reviews.sql`

Uma migração só, com as duas tabelas, o índice único `(product_id, lower(author_email))`, o índice de leitura `(product_id, status, created_at desc)` e o único em `token_hash`. Flyway commitado é imutável (constituição §5): correção vira `V8`.

`status` como `text` com `check`, e não `enum` nativo do Postgres — é exatamente o que a `V3` fez em `product.status` (`constraint product_status_valido check (status in (…))`). Mudar de estilo agora criaria duas convenções no mesmo banco, e `enum` do Postgres precisa de migração para ganhar valor novo.

### DT-09 — Rate limit

`shared/web/LimitadorDeTaxa.java`: janela deslizante por IP, 1/minuto e 5/hora, resposta **429** com `Retry-After`. Aplicado só no `POST` público de review e no reenvio.

O IP vem de `X-Forwarded-For` quando houver proxy à frente — **e isso é configurável**, porque confiar no header sem proxy na frente é deixar qualquer um forjar o IP. Default: usar o IP da conexão.

### DT-10 — Frontend: o primeiro POST da aplicação

A 002 inteira é leitura, com `<form method="GET">`. Agora entra escrita, e a regra do AC-17 é a mesma: **funciona sem JavaScript**.

- **Server Action** (`"use server"`) como `action` do `<form>`. É o mecanismo do Next que degrada sozinho: sem JS o navegador faz o POST e o servidor re-renderiza a página com o estado devolvido. Um route handler exigiria montar redirect e serializar erro na URL à mão.
- O estado de erro volta por `useActionState`, **com os campos preenchidos** — perder 800 caracteres de texto por causa de um e-mail inválido é o tipo de coisa que faz a pessoa desistir e não voltar.
- A action chama a API pelo cliente `server-only` que já existe. `API_BASE_URL` continua fora do navegador (AC-24).
- **Risco registrado (R-02):** o Next documenta a degradação sem JS, e a 002 não exercitou isso. O e2e no projeto `chromium-no-js` é o que prova — e se não funcionar, o plano B é um route handler com `POST` + `redirect`, sem mudar a tela.

### DT-11 — Testes: a primeira suíte que escreve

A constituição §6 proíbe a suíte compartilhada de escrever. Uma review publicada muda a média que outro teste afirma, e os workers rodam em paralelo — então:

- **instância dedicada no compose**, perfil `e2e-escrita`: `db-escrita` + `app-escrita` + `web-escrita`, mesmas imagens, banco próprio;
- projeto novo do Playwright, `escrita`, com `baseURL` apontando para ela (é o mesmo padrão do `resiliencia`, que a 002 criou para a instância sem API);
- **Mailpit** no mesmo perfil: o teste lê a API dele (`GET /api/v1/messages`) para pegar o link de confirmação. Sem isso, o fluxo principal não é testável de ponta a ponta;
- cada teste usa e-mail único (`uuid@teste.local`), como a constituição §6 exige.

**A smoke continua com cinco casos.** Escrever review não entra: o orçamento é fixo e o caso pede duas instâncias e uma caixa de e-mail. Ele vive na suíte completa.

### DT-12 — CI

- O job `backend` não muda.
- O job `web` sobe o compose com `--profile e2e` e agora também `--profile e2e-escrita`? **Não.** A smoke não toca a instância de escrita, e subir três containers a mais por push custa ~1 min sem cobrir nada novo. A instância de escrita sobe **só** no `e2e-full.yml`, junto com o Mailpit.
- O `e2e-full.yml` ganha o perfil novo no passo de compose — e este plano lembra que **foi exatamente esse tipo de esquecimento** que deixou a full quebrada por cinco tarefas na 002 (T05).

## 2. Estrutura de arquivos

```
src/main/java/br/com/saasfinder/reviews/
  api/         ReviewController.java · AdminReviewController.java
               ReviewRequest.java · ReviewView.java · RatingView.java
  domain/      Review.java · ReviewStatus.java · ReviewVerification.java
               ReviewService.java · ReviewFacade.java
               EnviadorDeEmail.java · TransicaoInvalidaException.java
  infra/       ReviewRepository.java · ReviewAggregateJdbc.java
               mail/SmtpEnviadorDeEmail.java · mail/Mensagens.java
               ProvedoresGratuitos.java   (lê a lista versionada)

src/main/resources/
  db/migration/V7__reviews.sql
  reviews/provedores-gratuitos.txt        um domínio por linha, versionado
  mail/confirmacao.pt-BR.txt · confirmacao.en.txt
       publicada.pt-BR.txt   · publicada.en.txt

web/
  app/[locale]/produtos/[slug]/avaliar/page.tsx   · products/[slug]/review/
  app/[locale]/reviews/confirmar/page.tsx         · reviews/confirm/
  app/[locale]/politica-de-reviews/page.tsx       · review-policy/
  src/paginas/avaliar.tsx · confirmacao.tsx · politica-de-reviews.tsx
  src/components/reviews/  FormularioDeReview.tsx · ListaDeReviews.tsx
                           ReviewCard.tsx · NotaResumo.tsx · SeloVerificado.tsx
  src/lib/reviews/acoes.ts        Server Action
  src/lib/reviews/validacao.ts    regra pura, testável sem servidor
```

`politica-de-reviews` é conteúdo, não componente: o texto vive no catálogo de mensagens, como todo o resto da 002.

## 3. Rastreabilidade — critério de aceite → onde vive

| AC | Onde | Tipo de teste |
|---|---|---|
| AC-01, AC-02 | `ReviewController`, `ReviewService` | integração |
| AC-03, AC-04 | `ReviewVerification`, `ReviewService` | integração |
| AC-05 | índice único + resposta 202 | integração |
| AC-06 | `ReviewRequest` (Bean Validation) | integração |
| AC-07 | `@TransactionalEventListener` + enviador falso | integração |
| AC-08, AC-09 | `AdminReviewController` | integração |
| AC-10 | `ReviewView` (o que o DTO **não** tem) | integração |
| AC-11, AC-12, AC-13, AC-13a | `ReviewAggregateJdbc` | integração |
| AC-14 | `ReviewFacade.notasDe` | integração que **conta consultas** |
| AC-15 | `ReviewCard` | componente |
| AC-16 | `Mensagens` | unidade |
| AC-17 | `FormularioDeReview` + Server Action | e2e `@no-js` |
| AC-18, AC-19, AC-20 | telas | e2e (projeto `escrita`) |
| AC-21, AC-21a | axe + rota da política | e2e |
| AC-21b, AC-21c | política + `moderation_note` | e2e + integração |
| AC-22, AC-23 | compose e `playwright.config.ts` | manual + e2e |

## 4. Riscos

| # | Risco | Mitigação |
|---|---|---|
| R-01 | **Fila de moderação vazia por semanas.** Sem tráfego, ninguém avalia, e a fatia inteira fica sem exercício real | O seed ganha reviews de exemplo? **Não** — seria dado inventado (constituição §7.5). O exercício vem do e2e e da instância de escrita. Aceitar que a validação real depende de tráfego |
| R-02 | **Server Action sem JavaScript** pode não degradar como o Next documenta | **Não se materializou (T12, 2026-08-27).** Com o motor desligado, o navegador faz o POST, a action valida e o servidor devolve a página re-renderizada — erro com os campos preenchidos, e sucesso via `redirect`, que o navegador segue sozinho. O plano B (route handler) **não foi necessário**. O que exigiu projeto novo no Playwright foi o alvo: o formulário escreve, então o caso precisa da instância de escrita **e** do motor desligado ao mesmo tempo — daí o projeto `escrita-no-js`. **Uma armadilha real apareceu no caminho:** num módulo `"use server"`, todo export tem de ser função async; exportar o estado inicial de lá fazia ele chegar `undefined` ao componente, e a tela quebrava em tempo de execução, não no build. O estado mudou para `src/lib/reviews/estado.ts`. |
| R-03 | **SMTP em produção não existe ainda** | O adapter é o mesmo; muda a variável. Em desenvolvimento, Mailpit. Nada no domínio sabe qual é |
| R-04 | **Rate limit em memória some no restart** e não vale entre instâncias | Documentado no código e no README. Enquanto houver uma instância, resolve; com duas, trocar por Redis é uma tarefa, não uma reescrita |
| R-05 | **Três containers a mais** no `docker compose up` do desenvolvedor | O perfil `e2e-escrita` é opt-in: `docker compose up` continua subindo db + app + web |
| R-06 | **A média muda a ordenação da lista?** Não nesta fatia — mas alguém vai supor que sim | A spec §2 diz que ranking é o 004. O `plan` do 004 é que decide |

## 5. Desvios propostos à spec

Nenhum. A spec foi escrita depois do plano de produto e já absorveu os cortes.

## 6. Fora deste plano (confirmando a spec §2)

`vendor`, claim, painel, lead, newsletter, cobrança, resposta do fornecedor dentro do produto, voto de utilidade, denúncia pelo visitante, tela de moderação, tradução automática, peso 0,5 e fórmula de ranking (é o 004), e-mail de 14 dias pedindo review (depende da tabela `event`).
