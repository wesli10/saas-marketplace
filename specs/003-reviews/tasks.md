# Tasks 003 — Reviews verificadas

| Campo | Valor |
|---|---|
| Spec | `specs/003-reviews/spec.md` |
| Plano | `specs/003-reviews/plan.md` |
| Constituição | `CLAUDE.md` (raiz) |
| Total | 16 tarefas (T00–T15) — **16 concluídas, 0 pendentes** |
| Data | 2026-08-27 |

## Como usar

- **Uma tarefa por commit.** Mensagem começa com `[003-TNN]`.
- Uma tarefa só está pronta quando o gate dela passa (`./mvnw verify` quando toca Java, `npm run verify` em `web/`) **e** o critério de verificação foi observado.
- **Confira o gate pelo código de saída, e depois de escrever tudo.** Nesta fatia isso já custou caro duas vezes: na 002 o `[002-T17]` entrou com duas falhas porque o filtro de saída escondia as linhas de erro, e o `[002-T21]` entrou fora de formato porque o `verify` rodou antes do último arquivo. `npm run verify; echo $?` **por último**.
- **Toda tarefa de tela entrega três coisas no mesmo commit:** a tela, os `data-testid` (constantes em `lib/testid.ts`) e o teste do AC correspondente.
- **A smoke continua com cinco casos.** Nenhum caso desta fatia entra nela (plano DT-11); um sexto exige justificativa no corpo do commit.
- **Migração Flyway commitada é imutável.** Correção vira `V8`.
- **Dependência nova exige uma linha no `plan.md` §DT-02 no mesmo commit.**
- **Rota nova é mudança de contrato.** O plano §DT-07 só fala em regenerar `web/src/lib/api/types.gen.ts` na T09, porque é ela que muda o *schema* — mas o CI compara o arquivo com o OpenAPI **inteiro**, e três rotas novas o deixam desatualizado do mesmo jeito. Aconteceu no primeiro push da `feature/reviews-escrita` (2026-08-27): backend verde, job `web` vermelho com *"types.gen.ts esta desatualizado"*. Toda tarefa que **acrescenta rota** roda `npm run api:types` com a API de pé e commita o arquivo junto.
- Se a implementação mostrar que a spec está errada, **para**, atualiza a `spec.md`, depois continua (constituição §2.5).
- **Mudança em workflow só está pronta quando roda.** O `e2e-full.yml` ficou cinco tarefas quebrado na 002 porque ninguém o executou — a T10 desta fatia não fecha sem uma execução verde.

---

## Fase 0 — Constituição e fundação

### T00 — Abrir a constituição para conteúdo de usuário ✅
**Status:** concluída em 2026-08-27 — `CLAUDE.md` §5 separou **texto de interface e conteúdo curado** (bilíngue obrigatório, inclusive o corpo dos e-mails que nós enviamos) de **conteúdo gerado por usuário** (um idioma, `locale` no registro, marcado na tela, nunca traduzido), e §7 ganhou a linha que põe traduzir opinião de terceiro na mesma proibição de inventar dado de produto. `grep -n "gerado por usuário" CLAUDE.md` → linha 64; `grep -in "todo texto visível" CLAUDE.md` → **sem resultado** (código 1).
**Objetivo:** hoje o `CLAUDE.md` §5 diz que *todo* texto visível ao usuário existe em pt e en. Enquanto isso for verdade no papel, cada review publicada é uma violação da constituição.
**Entrega:** §5 com a distinção entre **texto de interface e conteúdo curado** (bilíngue obrigatório) e **conteúdo gerado por usuário** (um idioma, carregado no próprio registro, marcado na tela, nunca traduzido automaticamente). Uma linha em §7 ("nunca fabricar") deixando explícito que traduzir opinião de terceiro entra na mesma proibição de inventar dado.
**Verificação:** `grep -n "gerado por usuário" CLAUDE.md` retorna a regra; `grep -n "todo texto visível" CLAUDE.md` não aparece mais sem a ressalva.
**ACs:** — (processo)

### T01 — `V7__reviews.sql` e o domínio, sem API ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **33 testes de unidade** (11 novos em `ReviewTransicoesTest`) e **237 de integração** (8 novos em `PersistenciaReviewIT`). As três transições inválidas da verificação estão cobertas uma a uma, mais publicar rejeitada. O índice único barrou o segundo `ana@acme.com.br` no mesmo produto — e também `ANA@Acme.COM.BR`, porque a entidade normaliza na entrada — e permitiu o mesmo e-mail no `acme-crm`. **Além do pedido:** os CHECKs de estado da V7 (`review_verificada_confirmou`, `review_publicada_tem_data`, `review_decidida_tem_autor`) viraram rede de segurança do domínio, e um teste prova que o banco recusa `verified = true` sem confirmação mesmo por `UPDATE` direto. `Review` guarda `product_id` como `UUID`, e não como `@ManyToOne Product`: mapear a entidade de `catalog` seria furar a fronteira do módulo pela porta dos fundos.
**Objetivo:** a base de tudo, e o lugar onde a máquina de estados vira código.
**Entrega:** migração com `review` e `review_verification` (plano DT-08), incluindo o índice único `(product_id, lower(author_email))`, o de leitura `(product_id, status, created_at desc)` e o único em `token_hash`; entidades, `ReviewStatus`, e as transições em `Review` (`confirmar`, `publicar`, `rejeitar`) lançando `TransicaoInvalidaException`.
**Verificação:** `./mvnw verify` verde. Teste de unidade cobre **cada transição inválida** — publicar sem confirmar, rejeitar publicada, confirmar duas vezes. Teste de integração prova que o índice único barra o segundo e-mail igual no mesmo produto e **permite** o mesmo e-mail em produtos diferentes.
**ACs:** — (base para AC-03, AC-05, AC-08)
**Depende de:** T00

### T02 — Provedores gratuitos e a regra do `verified` ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **41 testes de unidade** (8 novos em `ProvedoresGratuitosTest`) e **237 de integração** (inalterados). Os quatro casos da verificação passam pelo `Review.dominioDe`, que é o caminho de produção: `@empresa.com.br` e um `.coop.br` desconhecido ficam verificados, `@gmail.com` e `@GMAIL.COM` não. Arquivo vazio e arquivo ausente estouram `IllegalStateException` na construção do bean — e como ele é `@Component`, isso é o boot caindo: as 237 integrações subindo a aplicação são a prova de que a lista real carrega. A lista publicada tem **40 domínios**, cada bloco com fonte e data.
**Objetivo:** o selo é o que dá sentido à verificação, e a regra dele é pura — merece existir antes de qualquer controller.
**Entrega:** `src/main/resources/reviews/provedores-gratuitos.txt` versionado (um domínio por linha, com comentário de fonte e data, como o seed faz); `ProvedoresGratuitos` lendo o arquivo uma vez; `verified = confirmou && !gratuito`.
**Verificação:** unidade com `@empresa.com.br` (verificado), `@gmail.com` (não), `@GMAIL.COM` (não — comparação em minúsculas), domínio desconhecido (verificado). Arquivo vazio ou ausente **falha o boot**, e não silenciosamente marca todo mundo como verificado.
**ACs:** — (base para AC-13a)
**Depende de:** T01

---

## Fase 1 — Escrita e verificação

### T03 — Enviador de e-mail, com Mailpit no compose ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **49 testes de unidade** (8 novos: 5 em `MensagensTest`, 3 em `SmtpEnviadorDeEmailTest`) e **237 de integração**. Compose conferido com `--profile dev`: `mailpit` e `app` sobem `Healthy`, `localhost:8025/api/v1/messages` responde, e o `app` carrega `MAIL_HOST=mailpit` e `SITE_URL`. **O que a tarefa custou descobrir:** o `starter-mail` liga sozinho um health indicator que abre conexão SMTP a cada `/actuator/health` — duas integrações do 001 ficaram vermelhas com 503 antes de eu desligá-lo (`management.health.mail.enabled: false`, registrado no plano §DT-05). Com ele ligado, um SMTP fora do ar derrubaria o healthcheck do compose e o CI enquanto o catálogo inteiro continuaria respondendo. **Verificação manual fechada na T04** (2026-08-27), quando o POST passou a existir: dois envios reais pelo compose, um em `pt-BR` (Zapier) e um em `en` (Zendesk), apareceram no Mailpit com o assunto e o corpo certos e com o link apontando para `/pt-BR/reviews/confirmar` e `/en/reviews/confirm`. Nenhuma linha de log trouxe o e-mail completo nem o token — só `e-mail enviado para dominio empresa-da-ana.com.br`.
**Objetivo:** sem caixa de entrada local, o fluxo principal da fatia só é testável à mão.
**Entrega:** `EnviadorDeEmail` no domínio; `SmtpEnviadorDeEmail` com `JavaMailSender`; `Mensagens` com os textos nos dois idiomas (`MessageFormat`, sem engine nova); serviço `mailpit` no `docker-compose.yml` sob o perfil de desenvolvimento e `MAIL_*` no `.env.example`; linha nova no `plan.md` §DT-02.
**Verificação:** `./mvnw verify` verde com um enviador falso. Manual, uma vez: subir o compose, disparar um envio e ver a mensagem no Mailpit — **nos dois idiomas**, conferindo que o link aponta para a rota certa e que nem o e-mail completo nem o token aparecem no log.
**ACs:** AC-16
**Depende de:** T01

### T04 — `POST /products/{slug}/reviews` ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **49 testes de unidade** e **247 de integração** (10 novos em `ReviewEnvioIT`). Os cinco critérios da tarefa observados: 202 com a review em `PENDING_EMAIL`; segundo envio com `Ana@ACME.com.BR` respondendo **202 idêntico**, sem segunda review, sem segundo e-mail e sem sobrescrever a primeira; corpo curto, nota 6, `would_recommend` ausente e e-mail malformado respondendo 400 em `problem+json` com o campo em `errors[]`; enviador que lança exceção não desfaz a review (ela fica em `PENDING_EMAIL`, com o token gravado para o reenvio); e o `token_hash` do banco é o SHA-256 do token do e-mail, nunca ele mesmo. **Decisão de implementação:** o listener de `AFTER_COMMIT` engole a exceção. Sem isso, uma falha de envio vira 500 *depois* do commit — a pior combinação possível, porque quem escreveu acha que perdeu o texto que já foi gravado. **Verificação manual (fecha também a T03):** compose com `--profile dev`, um envio em cada idioma, as duas mensagens no Mailpit com o link certo, e nenhum e-mail completo nem token no log.
**Objetivo:** a porta de entrada. É aqui que a resposta 202 uniforme protege quem escreveu.
**Entrega:** `ReviewRequest` com Bean Validation (nota 1–5, corpo 80–2.000, `would_recommend` obrigatório, e-mail válido); criação em `PENDING_EMAIL`; token gerado e gravado só como hash (plano DT-04); envio disparado **depois do commit** (`AFTER_COMMIT`).
**Verificação:** integração: 202 e review criada; segundo envio com o mesmo e-mail responde **202 idêntico** e não cria a segunda; corpo curto e nota 6 respondem 400 em `problem+json` nomeando o campo; **enviador que lança exceção não desfaz a review** — ela fica em `PENDING_EMAIL`. O `token_hash` no banco não é igual ao token do e-mail.
**ACs:** AC-01, AC-02, AC-05, AC-06, AC-07
**Depende de:** T03

### T05 — Confirmação, reenvio e expurgo ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **49 testes de unidade** e **255 de integração** (8 novos em `ReviewConfirmacaoIT`). Observado: confirmar duas vezes dá 200 e depois 410, sem mexer no estado na segunda; token com `expires_at` no passado dá 410 e o reenvio gera um que funciona; o token anterior para de valer no reenvio e continua havendo **uma** linha de `review_verification`; reenvio para e-mail que nunca avaliou, para slug inexistente e para review já confirmada respondem 202 **sem mandar nada**; o expurgo apaga a de 31 dias, poupa a de 29 e a já confirmada, e a linha de verificação some junto pelo `on delete cascade`. **Decisão:** token desconhecido também responde 410, e não 404 — distinguir os três casos diria a quem chuta tokens quais deles um dia existiram, e a tela oferece o reenvio nos três. **Renomeado:** o evento `ReviewCriada` da T04 virou `ConfirmacaoSolicitada`, porque o reenvio publica o mesmo evento — o que dispara o e-mail não é a criação, é existir um token novo esperando.
**Objetivo:** fechar o ciclo do token — e não deixar dado pessoal que não virou nada apodrecendo no banco.
**Entrega:** `POST /reviews/confirm` (200 → `PENDING_MODERATION`; 410 para usado ou expirado); `POST /reviews/resend` (202 sempre, token novo **substituindo** o anterior); rotina agendada que apaga `PENDING_EMAIL` com mais de 30 dias.
**Verificação:** integração: confirmar duas vezes → 200 e 410; token de 49 h → 410 e o reenvio gera um que funciona; o token antigo para de valer depois do reenvio. A rotina apaga uma review de 31 dias e **não** toca numa de 29 nem numa já confirmada.
**ACs:** AC-03, AC-04
**Depende de:** T04

### T06 — Rate limit no que é público ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **58 testes de unidade** (9 novos em `LimitadorDeTaxaTest`) e **258 de integração** (3 novos, em `ReviewRateLimitIT` e `ReviewRateLimitProxyIT`). O limite de 5/hora é provado no teste de unidade, com relógio de mentira: esperar a sexta chamada de verdade custaria uma hora de suíte. As integrações provam a fiação com os valores de produção, em contexto próprio — segunda chamada no mesmo minuto responde 429 com `Retry-After` entre 1 e 60, forjar `X-Forwarded-For` **não** contorna com o proxy desligado, o reenvio divide o mesmo limite, a leitura não é limitada, e com o proxy ligado três IPs diferentes passam enquanto o mesmo IP encaminhado é barrado (inclusive com cadeia de proxies na lista). **Consequência registrada:** a suíte compartilhada passou a rodar com limites frouxos (`RATE_LIMIT_POR_MINUTO=1000`), porque vários testes mandam dois POSTs dentro do mesmo método de propósito — com o limite real, eles testariam o limitador em vez do que se propõem a testar. O motivo está escrito no `AbstractIntegrationTest`.
**Objetivo:** a spec §8 pede; sem isso um script enche a fila de moderação numa tarde.
**Entrega:** `LimitadorDeTaxa` (janela deslizante em memória, 1/min e 5/h por IP), aplicado ao POST de review e ao reenvio; resposta **429** com `Retry-After`; IP de `X-Forwarded-For` só quando configurado (plano DT-09), com o default no IP da conexão.
**Verificação:** integração: a segunda chamada no mesmo minuto responde 429 com `Retry-After`; a sexta na mesma hora idem; IP diferente não é afetado. Com o header configurado como não confiável, forjar `X-Forwarded-For` **não** contorna o limite.
**ACs:** — (spec §8)
**Depende de:** T04

---

## Fase 2 — Moderação e leitura

### T07 — Fila e decisões do curador ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **58 testes de unidade** e **267 de integração** (9 novos em `AdminReviewIT`); `npm run verify` verde em `web/` (13 arquivos, 77 testes) com os tipos regenerados. Observado: sem HTTP Basic as três rotas respondem 401 e nada muda de estado; aprovar não confirmada → 409; rejeitar com motivo em branco → 400, e com motivo grava `moderation_note`, `moderated_by` e `moderated_at`; a decisão de manter grava as três **sem** mudar o estado, e sobre review não publicada responde 409; o e-mail de publicação sai no idioma da review (`Your review of Acme Chat is live`, com link `/en/products/acme-chat`) e **nenhum** e-mail sai na rejeição (Q-02). **O que o gate pegou:** escrevi o controller em `reviews/api`, e o `OpenApiIT` falhou — o `OpenApiErrosCustomizer` identifica rota de curadoria **pelo pacote** `br.com.saasfinder.admin`, então o controller funcionava e a documentação mentia, sem exigir credencial nem declarar o 401. Movido para `admin/api`, como os controllers de curadoria do catálogo. **Além do pedido:** a fila traz `outras_do_mesmo_dominio`, o sinal que a spec Q-05 pôs no lugar do bloqueio por domínio — uma consulta agrupada para a página inteira, não uma por linha.
**Objetivo:** nada é publicado sem passar por uma pessoa, e toda decisão precisa deixar rastro.
**Entrega:** `GET /admin/reviews?status=&product=`; `approve`; `reject` com motivo obrigatório; `note` para registrar a decisão de **manter** depois de uma contestação; `moderation_note`, `moderated_by` e `moderated_at` gravados nas três; e-mail avisando o autor **quando publica** (não quando rejeita — spec §11, Q-02).
**Verificação:** integração: aprovar não confirmada → 409; rejeitar sem motivo → 400; sem HTTP Basic → 401; as três decisões gravam quem, quando e por quê. O e-mail de publicação sai no idioma da review.
**ACs:** AC-08, AC-09, AC-21c
**Depende de:** T05

### T08 — Leitura pública e agregado ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **58 testes de unidade** e **274 de integração** (7 novos em `ReviewLeituraIT`); `npm run verify` verde em `web/` com os tipos regenerados. Observado: só `PUBLISHED` aparece, da mais recente para a mais antiga; média `4,3` de notas 4, 5 e 4 escritas em `pt-BR` e `en`; `rating` **`null`** quando não há review publicada, mesmo com uma pendente no banco; `recommend_ratio` `0.67` e `verified_count` 2 de 3 (a de `@gmail.com` confirmou e ficou sem selo); o agregado é de todas as publicadas, e não da página — `size=1` traz um item e `count` 3. A ausência de `author_email`, `moderation_note`, `moderated_by` e `token_hash` é afirmada **sobre o JSON servido**, com uma nota de moderação e uma linha de verificação gravadas de propósito no banco para o teste ter o que vazar. **Desvio registrado no plano §DT-06:** a proporção sai arredondada em duas casas — a divisão crua devolve `0.666…67`, que vira ruído no contrato.
**Objetivo:** o que a ficha vai consumir.
**Entrega:** `GET /products/{slug}/reviews` (só `PUBLISHED`, paginado, mais recente primeiro) com o agregado da consulta única do plano DT-06: média com uma casa, contagem, proporção de "recomendaria" e quantas verificadas.
**Verificação:** integração: só publicadas aparecem; média com uma casa; **`rating` é `null`, e não `0`, quando não há review**; a média considera reviews dos dois idiomas. Nenhum campo de `review_verification`, `author_email`, `moderation_note` ou `moderated_by` no corpo servido — conferido no JSON, e não no DTO.
**ACs:** AC-10, AC-11, AC-12, AC-13, AC-13a
**Depende de:** T07

### T09 — A nota entra no contrato do catálogo ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde: **58 testes de unidade** e **280 de integração** (6 novos em `ProdutoNotaIT`); `npm run verify` verde em `web/`. **AC-14 observado:** uma listagem de 20 produtos faz **uma** chamada a `ReviewAggregateJdbc.notasDe`, com os 20 ids dentro dela — a contagem é sobre esse componente, que emite exatamente uma consulta por chamada, e não sobre o SQL bruto do build. A comparação de três fichas também faz uma só. `rating` aparece no card, na ficha e na comparação, e vem `null` para produto sem review. `OpenApiFidelidadeIT` verde, `specs/001-catalogo-core/spec.md` §10.1 emendada e `types.gen.ts` regenerado, tudo no mesmo commit. **Decisão:** `ProductSummaryView` ganhou um `id` **não serializado** (`@JsonIgnore`) — sem ele seria preciso uma segunda consulta só para traduzir de volta em id os slugs que a busca acabou de ler; a API pública continua falando por slug, e um teste afirma que `id` não aparece no JSON. **O que o gate pegou:** o `LogRedactionIT` quebrou com `FATAL: sorry, too many clients already` — cada contexto Spring em cache mantém um pool de 10 conexões, e a fatia foi somando contextos (dois de rate limit, um por combinação de bean substituído) até estourar as 100 conexões do Postgres. O pool dos testes passou a 4, com o motivo escrito no `AbstractIntegrationTest`.
**Objetivo:** a nota precisa aparecer onde as pessoas olham: no card e na ficha. E é a mudança que mais pode quebrar o que já existe.
**Entrega:** `ReviewFacade.notasDe(Collection<UUID>)`; `rating` em `ProductSummaryView` e `ProductDetailView`; **`specs/001-catalogo-core/spec.md` §10.1 emendada no mesmo commit**; OpenAPI regenerado; `web/src/lib/api/types.gen.ts` regenerado (plano DT-07).
**Verificação:** integração que **conta as consultas** de uma listagem de 20 produtos e afirma que a de nota é **uma só** (AC-14). `OpenApiFidelidadeIT` verde. `git show --stat` do commit inclui a spec do 001 e o `types.gen.ts`. `npm run verify` em `web/` verde — o typecheck é quem prova que o tipo novo chegou.
**ACs:** AC-14
**Depende de:** T08

---

## Fase 3 — A infraestrutura de teste que escreve

### T10 — Instância de escrita, Mailpit no e2e e o projeto novo do Playwright ✅
**Status:** concluída em 2026-08-27 — perfil `e2e-escrita` no compose com `db-escrita` (tmpfs, nasce limpo), `app-escrita` (porta 8081, seed ligado), `web-escrita` (porta 3002) e o Mailpit; projeto `escrita` no Playwright apontando para a 3002; helper que lê o link de confirmação pela API do Mailpit. `npm run e2e -- --project=escrita` verde (**3 testes**), e a suíte inteira verde: **313 testes**, seis projetos, em 29,3 s. `npm run verify` verde em `web/`. **Isolamento medido, e não deduzido:** depois da execução, `select count(*) from review` na instância compartilhada devolveu **0**, e na de escrita, **6**. A varredura `check-e2e-isolation.mjs` passou a distinguir as duas suítes: `POST` e `/admin/` continuam proibidos fora de `tests/e2e/escrita/` e passam a ser permitidos dentro; em compensação, **e-mail literal dentro da escrita agora falha** a varredura, porque o índice único da V7 é por (produto, e-mail) e endereço reaproveitado faz o segundo teste não criar nada. Os dois gatilhos foram exercitados uma vez, e os dois saíram com código 1. **A `e2e-full.yml` rodou verde** (run #28, `workflow_dispatch` sobre a `feature/reviews-infra-teste`, **10m35s**): o workflow sobe os dois perfis, e os 313 testes passam no CI, não só na máquina. É o que faltou na 002, quando ele ficou cinco tarefas quebrado por nunca ter sido executado. **E rodou também pelo gatilho da promoção** (2026-08-28): a promoção para `homol` disparou o `e2e full` #23, **12m0s**, com **353 testes** na suíte completa (2,8 min) e **212** na topologia invertida (1,1 min). Com isso a tarefa fecha inteira — era exatamente esse caminho que ficou sem exercício na 002, e por isso o workflow passou cinco tarefas quebrado.
**Objetivo:** a constituição §6 proíbe a suíte compartilhada de escrever, e a partir da T11 os testes escrevem. Esta tarefa vem **antes** das telas de propósito.
**Entrega:** perfil `e2e-escrita` no compose com `db-escrita`, `app-escrita`, `web-escrita` e `mailpit`; projeto `escrita` no `playwright.config.ts` apontando para a instância nova; helper que lê o link de confirmação pela API do Mailpit; o perfil novo no passo de compose do **`e2e-full.yml`**.
**Verificação:** `npm run e2e -- --project=escrita` verde com um caso de fumaça que cria uma review e a vê na fila. A suíte compartilhada continua sem escrever (`check-e2e-isolation.mjs` verde). **E a `e2e-full.yml` roda verde numa promoção para `homol`** — sem essa execução a tarefa não fecha (foi o erro do `[002-T05]`).
**ACs:** AC-22, AC-23
**Depende de:** T08

---

## Fase 4 — Telas

### T11 — Bloco de reviews na ficha ✅
**Status:** concluída em 2026-08-27 — `npm run verify` verde (**89 testes de unidade**, 12 novos em `reviews-ficha.test.tsx`) e a suíte e2e inteira verde (**317 testes**, 4 novos na suíte de escrita). Componente: card sem `verified` não mostra o selo **nem o domínio**; média sai `4,3` em pt-BR e `4.3` em en, pelo `Intl`; produto sem review diz isso em vez de mostrar `0,0`. E2e na instância de escrita: a nota da tela é comparada com a que a API acabou de devolver (e não com um número escrito no teste), e uma review em `en` na ficha em `/pt-BR` aparece com `data-idioma="en"` e `lang="en"` no corpo — a mesma review lida em `/en` não ganha marcação nenhuma. **Falha de API vira aviso no bloco, e não página de erro:** avaliação é conteúdo adicional, e derrubar a ficha inteira porque um endpoint secundário piscou trocaria uma perda pequena por uma grande; sumir em silêncio seria indistinguível de "ninguém avaliou". **Três contradições da spec corrigidas** (constituição §2.5): AC-19, §3 e §4.3 ainda prometiam distribuição por estrela e filtro por idioma, que a §6 corta. **Dívida conhecida:** o link para a política aponta para uma rota que só existe na T14.
**Entrega:** `NotaResumo` (média, contagem, selo), `ListaDeReviews` paginada, `ReviewCard` com a marcação de idioma diferente; link para a política; `data-testid` novos.
**Verificação:** e2e: a nota exibida bate com a da API; review em `en` na ficha em `/pt-BR` aparece marcada como escrita em inglês. Componente: card sem `verified` não mostra o selo.
**ACs:** AC-15, AC-19
**Depende de:** T09, T10

### T12 — Formulário de review, sem JavaScript ✅
**Status:** concluída em 2026-08-27 — `npm run verify` verde (**96 testes de unidade**, 7 novos em `reviews-validacao.test.ts`) e a suíte e2e inteira verde (**323 testes**, 6 novos). **A Server Action degradou:** com o motor desligado, o envio funciona, a URL vira `?enviado=1` e a tela de "em análise" é HTML servido; um erro de validação volta com o título, o corpo, o nome e o e-mail que a pessoa escreveu. O plano B do §DT-10 **não foi necessário** — risco R-02 fechado no plano. Precisou de um projeto novo no Playwright, `escrita-no-js`: o formulário escreve, então o caso exige a instância de escrita **e** o motor desligado, e nenhum dos projetos existentes juntava as duas coisas. **Duas coisas que o caminho ensinou:** num módulo `"use server"` todo export tem de ser função async — exportar o estado inicial de lá fazia ele chegar `undefined` e a tela quebrava só em tempo de execução; e a varredura `check-testids.mjs` pegou os `<input type="radio"` sem id, o que me fez trocar o seletor por posição (`nth(3)`) por testid mais `data-nota`, que não quebra em silêncio se a ordem mudar.
**Objetivo:** o primeiro POST da aplicação, e o AC que corre mais risco (plano R-02).
**Entrega:** rota `avaliar` nos dois idiomas; `FormularioDeReview` com Server Action; `validacao.ts` puro; erro voltando **com os campos preenchidos**; tela de "em análise" depois do envio.
**Verificação:** e2e no projeto `escrita`: enviar cria a review e a tela diz que está em análise; a review **não** aparece na ficha. e2e `@no-js`: o mesmo envio funciona com JavaScript desligado, e um erro de validação volta com o texto que a pessoa escreveu. **Se a Server Action não degradar, trocar pelo plano B do DT-10 e registrar aqui.**
**ACs:** AC-17, AC-18
**Depende de:** T11

### T13 — Página de confirmação ✅
**Status:** concluída em 2026-08-27 — `./mvnw verify` verde (**58 unidade / 280 integração**), `npm run verify` verde (**96 unidade**) e a suíte e2e inteira verde (**328 testes**, 5 novos). O caminho feliz é de ponta a ponta de verdade: envia pela API, **pega o link no Mailpit, abre o próprio link no navegador** e confere a fila do curador — é o único teste que prova que o endereço montado pelo backend e a rota servida pelo frontend são a mesma coisa, já que as duas metades vivem em módulos que não se enxergam. Os casos de já usado e de token forjado caem na mesma tela, com o reenvio à mão, e o reenvio manda um link novo. **Mudança que a tela exigiu no backend:** o link do e-mail passou a carregar `&produto={slug}`. Sem ele, a tela de link expirado não teria o que reenviar — a API pede produto **e** e-mail, e quem clicou tarde demais é justamente quem já perdeu o fio. Dois testes de integração precisaram parar o token no `&`. **Decisão a revisar:** a confirmação acontece no GET da página, e não atrás de um botão — é o que a spec §6 desenha, e evita um segundo clique de quem já disse o que queria; o custo é que um scanner de e-mail que siga links pode queimar o token, e o caminho de volta é o reenvio que a própria tela oferece.
**Entrega:** rota `reviews/confirmar` nos dois idiomas, tratando sucesso, expirado (com botão de reenvio) e já usado.
**Verificação:** e2e no projeto `escrita`, ponta a ponta: enviar → **pegar o link no Mailpit** → confirmar → ver a review na fila do admin. Os casos de expirado e já usado com token forjado.
**ACs:** AC-20
**Depende de:** T12

### T14 — Política de reviews ✅
**Status:** concluída em 2026-08-27 — `npm run verify` verde (**96 unidade**) e a suíte e2e inteira verde (**341 testes**, 5 novos). A política responde nos dois idiomas com a rota traduzida, entra no `sitemap.xml` nas duas versões, e os dois links levam até ela — o do bloco de reviews e o do formulário —, inclusive **sem JavaScript**. O endereço de contestação sai como `mailto:` de verdade: contestação que depende de alguém copiar texto da tela é uma contestação a menos. **O que fica na sua mão:** o endereço e o prazo são **compromissos públicos**, então vêm do ambiente (`REVIEWS_CONTATO`, `REVIEWS_PRAZO_DIAS`) e não do código — inventá-los seria publicar uma promessa que ninguém fez. O padrão usa `example.com`, que a IANA reserva para documentação, justamente para não parecer um endereço real que ninguém lê. **Efeito colateral tratado:** o `sitemap.spec.ts` do 002 contava as URLs exatas e passou a contar a política junto.
**Objetivo:** é o que dá lastro ao selo, e é exigência do plano de produto §9.
**Entrega:** rota bilíngue com quem pode avaliar, o que é verificado, o que é removido, **o endereço de contestação com prazo** e a regra de que review só sai por violação de política; entrada no `sitemap.xml`; link do bloco de reviews e do formulário.
**Verificação:** e2e: responde nos dois idiomas, aparece no `sitemap.xml`, e os dois links levam até ela.
**ACs:** AC-21a, AC-21b
**Depende de:** T11

---

## Fase 5 — Fechamento

### T15 — Axe, varreduras e README ✅
**Status:** concluída em 2026-08-27 — todos os gates verdes: `./mvnw verify` (**58 unidade / 280 integração**), `npm run verify` (**96 unidade**), `npm run e2e` (**353 testes**, sete projetos) e `npm run e2e:isolamento` (**212 execuções** na topologia invertida). Axe sem violação crítica nem séria nas quatro telas novas: formulário e política entraram na varredura compartilhada; a ficha **com avaliação publicada**, a tela de "em análise" e a de confirmação (sucesso e link inválido) ganharam axe próprio na suíte de escrita — rodá-las na compartilhada exigiria publicar uma avaliação lá, que é o que a constituição §6 proíbe. `check-seed-leak.mjs` verde com os arquivos novos (**49 termos conferidos**). **AC-01 remedido com a stack inteira:** `down -v` e `up --build -d --wait` com os três perfis levou **51 s** e subiu os **oito** serviços saudáveis, com 54 URLs no `sitemap.xml`. **A demo da spec §1 foi executada nessa stack recém-subida:** 202 no envio, e-mail em português no Mailpit, 200 na confirmação, 200 na aprovação, aviso de publicação saindo, e a nota `4,0` aparecendo no card e na ficha em `/pt-BR` e `4.0` em `/en` — com o selo `empresa-da-ana.com.br`. Nenhum e-mail completo nem token no log.
**Entrega:** axe nas telas novas (formulário, ficha com reviews, confirmação, política) dentro da suíte completa; `check-seed-leak.mjs` continua verde com os arquivos novos; seção de reviews no `README.md`, com o fluxo, as variáveis `MAIL_*` e como subir o Mailpit; AC-01 remedido com a stack inteira e o tempo anotado.
**Verificação:** axe sem violação crítica nem séria; `npm run verify` e `./mvnw verify` verdes; `docker compose down -v && docker compose up --build` do zero conferido e cronometrado.
**ACs:** AC-21, AC-01 (manual)
**Depende de:** T13, T14

---

## Ordem sugerida de branches

| Branch | Tarefas |
|---|---|
| `feature/reviews-fundacao` | T00–T02 |
| `feature/reviews-escrita` | T03–T06 |
| `feature/reviews-moderacao` | T07–T09 |
| `feature/reviews-infra-teste` | T10 |
| `feature/reviews-telas` | T11–T14 |
| `feature/reviews-fechamento` | T15 |

Cada branch abre PR automático para `dev`, como nas duas fatias anteriores.

## O que esta fatia **não** muda

A smoke continua com os cinco casos da 002. Escrever review pede duas instâncias e uma caixa de e-mail; o caso vive na suíte completa, e o orçamento de 5 minutos da smoke fica intacto.
