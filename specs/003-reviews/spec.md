# 003 — Reviews verificadas

| Campo | Valor |
|---|---|
| Constituição | [`CLAUDE.md`](../../CLAUDE.md) |
| Depende de | 001-catalogo-core (produto, admin), 002-frontend-catalogo (ficha, listas) |
| Estado | **implementada** — 16 tarefas (T00–T15), fechadas em 2026-08-27 |
| Data | 2026-08-27 |

---

## 1. Objetivo

Deixar quem **usa** o produto falar sobre ele, com credibilidade verificável e sem conta de usuário: a pessoa escreve a review com o e-mail de trabalho, confirma por um link, um curador aprova, e ela aparece na ficha com a nota média do produto.

É a fatia que transforma o catálogo de vitrine curada em opinião de mercado — e é a matéria-prima do ranking (004).

**Demo de sucesso:** na ficha do Zapier em `/pt-BR`, escrever uma review com nota 4 e um e-mail `@empresa.com.br` → receber o link no Mailpit e confirmar → a review some da tela pública e aparece na fila de moderação → o curador aprova por `POST /api/v1/admin/reviews/{id}/approve` → recarregar a ficha e ver a review publicada, com o selo do domínio, e a nota do produto passar a `4,0 (1 avaliação)` nas duas versões de idioma.

## 2. Fora de escopo (explícito)

- **Conta de usuário e login.** A verificação aqui é **por review**, não por sessão: um token de uso único enviado por e-mail. Quem substitui isso é a 006-identidade, e ela vai encontrar as reviews já com o e-mail do autor guardado.
- **`vendor`, claim de listagem, painel do vendedor, captura de lead, "quero uma demo", newsletter e qualquer forma de cobrança.** Decisão de produto de 2026-08-27: **a plataforma é vitrine.** Ela mostra o máximo que conseguir sobre o SaaS e redireciona para o site dele — o link externo que a ficha já tem (`rel="nofollow sponsored noopener"`). Tudo que envolve vender abre frente jurídica (contrato com vendedor, responsabilidade sobre lead, repasse, nota fiscal) e fica para quando houver revisão jurídica de verdade. Isso confirma o single-sided do plano §2.1, com um motivo diferente do dele.
- **Resposta do fornecedor dentro do produto.** Sem `vendor`, não há painel para responder. O que existe é o canal de contestação do §7.1 — e ele não é opcional.
- **Peso da nota no ordenamento.** A média é exposta pela API; quem decide o que ela faz com a posição do produto é a 004-ranking-v1.
- **Tradução de review.** Ver §4.3: review não se traduz, e inventar tradução automática violaria a constituição §7.
- **Voto de utilidade** ("esta review foi útil") e **denúncia pelo visitante**. A moderação desta fatia é prévia, não comunitária.
- **Imagem, print ou anexo** na review.
- **Interface de moderação.** A curadoria segue pela API, como no 001. Uma tela de admin é uma fatia própria, e ela precisa da 006 antes.
- **Reputação do autor** (histórico, badges). O autor não tem identidade persistente nesta fatia.

## 3. Usuários e cenários

**Quem usa o SaaS.** Abre a ficha, clica em "avaliar", dá uma nota de 1 a 5, escreve o texto, informa nome de exibição, cargo (opcional) e o e-mail de trabalho. Recebe um link, confirma, e vê uma página dizendo que a review está em análise. Não cria conta, não escolhe senha, não volta para acompanhar nada.

**Visitante.** Lê as reviews na ficha, ordenadas da mais recente para a mais antiga, com a nota média e a contagem. Sabe quais são verificadas e de que domínio. Vê também as escritas no outro idioma, marcadas como tal. *(Corrigido em 2026-08-27, na T11: dizia "distribuição por estrela", que a §6 corta.)*

**Curador.** Lista a fila de pendentes por API, lê, aprova ou rejeita com um motivo. Nada é publicado sem passar por ele.

## 4. Modelo de domínio

### 4.1 `review`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | uuid v7 | |
| `product_id` | uuid | FK; review pertence a um produto, não a uma categoria |
| `rating` | smallint | 1 a 5, inteiro. Sem meia-estrela: precisão que ninguém consegue calibrar vira ruído |
| `would_recommend` | boolean | **"Recomendaria?"** — obrigatório. Não é enfeite: o ranking do 004 usa o limite inferior de Wilson sobre a proporção de "sim" (plano §5.1), e sem este campo a fórmula publicada não fecha |
| `title` | varchar(120) | obrigatório |
| `body` | text | 80 a 2.000 caracteres. O piso existe para barrar "bom" e "não gostei", que não ajudam ninguém a decidir |
| `locale` | varchar(5) | `pt-BR` ou `en`. **O idioma em que foi escrita**, não o da interface de quem lê (§4.3) |
| `author_name` | varchar(80) | nome de exibição, informado por quem escreve |
| `verified` | boolean | confirmou o e-mail **e** o domínio não é de provedor gratuito. É o que o 004 usa para o peso 0,5 das não verificadas (plano §5.2) e o que a tela mostra como selo |

**Cortado do plano §4.1 para caber no MVP:** `role` (cargo) e `company_size` (porte). Os dois falam de **quem escreveu**, e não do SaaS — que é o que a plataforma existe para mostrar. Cada campo a mais no formulário derruba a taxa de resposta, e review de verdade em pouca quantidade vale mais do que review completa em quantidade nenhuma. Entram quando houver volume que justifique segmentar ("o que dizem as empresas do seu porte").
| `author_email` | varchar(320) | **nunca sai em rota pública.** Serve para o link de confirmação e para a unicidade. Guardado em minúsculas; a unicidade é por índice sobre `lower(author_email)` — `citext` exigiria uma extensão nova, e o 001 só habilitou `unaccent`, `pg_trgm` e `vector` |
| `author_domain` | varchar(255) | derivado do e-mail na criação, e o que aparece na tela |
| `status` | enum | `PENDING_EMAIL` → `PENDING_MODERATION` → `PUBLISHED` \| `REJECTED` |
| `moderation_note` | varchar(300) | o motivo da decisão do curador — obrigatório ao rejeitar, e usado também ao **manter** depois de uma contestação (§7.1). Nunca sai em rota pública |
| `moderated_by` / `moderated_at` | varchar(80) / timestamptz | quem decidiu e quando. Sem isso não há como demonstrar que houve análise |
| `created_at` / `updated_at` | timestamptz | |
| `confirmed_at` / `published_at` | timestamptz | |

- **Um review por (produto, e-mail).** Índice único. A segunda tentativa responde igual à primeira (§5.1) — dizer "você já avaliou" revelaria que aquele e-mail avaliou aquele produto para quem apenas chutou o endereço.
- **`status` é uma máquina de estados**, e não um par de booleanos: `PENDING_EMAIL` sem confirmação não vai para a fila do curador, e review rejeitada não volta para pendente.

### 4.2 `review_verification`

| Campo | Tipo | Regra |
|---|---|---|
| `review_id` | uuid | FK, único |
| `token_hash` | varchar(64) | SHA-256 do token. **O token em claro nunca é persistido** — quem lê o banco não pode confirmar review de outra pessoa |
| `expires_at` | timestamptz | 48 h após a emissão |
| `used_at` | timestamptz | uso único |

Token expirado ou já usado responde 410, com a opção de reenviar. Review em `PENDING_EMAIL` há mais de 30 dias é apagada por rotina — dado pessoal que não virou nada não fica guardado.

### 4.3 Idioma: a exceção que esta fatia abre

A constituição §5 diz que **todo texto visível ao usuário existe em pt e en**. Isso vale para texto de interface e para conteúdo **curado** — e não pode valer para conteúdo escrito por terceiros:

- uma review existe **num idioma só**, o de quem escreveu;
- traduzir automaticamente seria pôr palavras na boca de uma pessoa identificável, e a constituição §7 proíbe inventar dado de produto — inventar opinião é pior;
- esconder as reviews do outro idioma desperdiçaria metade do conteúdo num catálogo bilíngue de mercado pequeno.

**Decisão:** a review carrega o próprio `locale`; a ficha mostra **todas**, com as do outro idioma marcadas ("escrita em inglês"). A média do produto considera todas, independentemente do idioma. *(Corrigido em 2026-08-27, na T11: dizia "e um filtro por idioma", que a §6 corta junto com a distribuição e a ordenação — com cinco reviews, filtrar é enfeite.)*

**Esta fatia emenda a constituição §5** com a distinção entre texto de interface/curadoria (bilíngue obrigatório) e conteúdo gerado por usuário (um idioma, marcado). A emenda entra na T00, antes de qualquer código — como o 002 fez com o §3.

### 4.4 Agregado de nota

`rating_average` (uma casa decimal) e `rating_count` são **calculados na consulta**, sobre reviews `PUBLISHED`. Nada de coluna denormalizada nesta fatia: com 20 produtos e dezenas de reviews, o custo é irrelevante, e coluna que se atualiza por gatilho é uma segunda fonte da verdade que diverge no primeiro `UPDATE` esquecido. Se a 004 provar que precisa de materialização para ranquear, ela materializa — com o número certo já definido aqui.

Produto sem review publicada devolve `rating: null`, e não `0` — nota zero é uma avaliação péssima, ausência de nota é outra coisa.

### 4.5 E-mail corporativo

`author_domain` sai do que vem depois do `@`. Provedores de e-mail gratuito (`gmail.com`, `outlook.com`, `hotmail.com`, `yahoo.com`, …) entram numa **lista versionada em arquivo**, como o seed — dado que muda com o tempo não vira constante em código.

Review de domínio gratuito **é aceita**, e aparece sem o selo (`verified = false`): bloquear excluiria a PME que usa Gmail para tudo, que é exatamente o público do catálogo. O que o selo diz é "esta pessoa confirmou um e-mail em `@acme.com.br`", e não "esta review é melhor". O plano §5.2 dá a essas reviews **peso 0,5 no score** — quem aplica isso é a 004; esta fatia só precisa gravar o `verified` certo.

**Divergência com o plano, e é deliberada.** O plano §5.2 diz "uma review por **domínio** de e-mail por produto". Aplicado ao pé da letra, dois funcionários da mesma empresa não poderiam avaliar o mesmo produto — e, pior, **uma única review de `@gmail.com` por produto** travaria todas as outras do maior "domínio" do país. Proposta: unicidade **por e-mail** (§4.1), e o limite por domínio vira sinal de moderação (a fila destaca quando já há review do mesmo domínio), não bloqueio. Ver Q-05.

## 5. API

### 5.1 Público

| Método | Rota | O quê |
|---|---|---|
| POST | `/api/v1/products/{slug}/reviews` | Cria em `PENDING_EMAIL` e dispara o e-mail. **Responde 202 sempre** que a entrada é válida — inclusive quando já existe review daquele e-mail para aquele produto |
| POST | `/api/v1/reviews/confirm` | `{ token }`. 200 e passa para `PENDING_MODERATION`; 410 se expirado ou usado |
| POST | `/api/v1/reviews/resend` | `{ product_slug, email }`. 202 sempre, pelo mesmo motivo do POST |
| GET | `/api/v1/products/{slug}/reviews` | Só `PUBLISHED`, paginado, da mais recente para a mais antiga. Traz o agregado: média, contagem, proporção de "recomendaria" e quantas são verificadas |

`GET /products` e `GET /products/{slug}` passam a trazer `rating: { average, count }` — **emenda ao contrato do 001**, registrada no §10.1 daquela spec no mesmo commit.

Nenhuma rota pública devolve `author_email`, `moderation_note`, `moderated_by` ou qualquer campo de `review_verification`.

### 5.2 Admin (HTTP Basic, como no 001)

| Método | Rota | O quê |
|---|---|---|
| GET | `/api/v1/admin/reviews?status=&product=` | Fila, com o texto completo e o domínio do autor |
| POST | `/api/v1/admin/reviews/{id}/approve` | `PENDING_MODERATION` → `PUBLISHED` |
| POST | `/api/v1/admin/reviews/{id}/reject` | `{ motivo }` obrigatório |
| POST | `/api/v1/admin/reviews/{id}/note` | Registra a decisão de **manter** depois de uma contestação, com o motivo (§7.1) |

Aprovar review que não está em `PENDING_MODERATION` responde 409 — inclusive a que ainda não confirmou o e-mail.

### 5.3 Envio de e-mail

- SMTP por variável de ambiente (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`).
- **No compose, um Mailpit** no perfil de desenvolvimento e de teste: o e2e lê a API dele para pegar o link de confirmação. Sem isso, o fluxo mais importante da fatia só seria testável à mão.
- O corpo do e-mail existe **nos dois idiomas** — é texto nosso, não do usuário —, e sai no idioma da página onde a review foi escrita.
- Falha no envio **não** perde a review: ela fica em `PENDING_EMAIL` e o reenvio funciona. E-mail é o único ponto desta fatia que depende de terceiro; ele não pode derrubar a transação.

## 6. Telas (spec 002 continua valendo)

| Tela | O quê muda |
|---|---|
| Ficha do produto | Bloco de reviews: média, contagem, lista paginada da mais recente para a mais antiga, marcação de idioma diferente, e o botão "avaliar". **Sem distribuição por estrela, sem filtro e sem ordenação** — os três só significam alguma coisa com volume, e com cinco reviews viram enfeite |
| Formulário de review | Página própria (`/pt-BR/produtos/{slug}/avaliar` · `/en/products/{slug}/review`), `<form method="POST">` que funciona sem JavaScript |
| Confirmação | `/pt-BR/reviews/confirmar?token=` — sucesso, expirado (com reenvio) e já usado |
| Lista e cards | A nota média aparece no card do produto |
| **Política de reviews** | Página pública e bilíngue (`/pt-BR/politica-de-reviews` · `/en/review-policy`): quem pode avaliar, o que é verificado, o que é removido e o que acontece com review incentivada. Exigida pelo plano §9, e é o que dá lastro ao selo de verificado |

- O formulário é o **primeiro POST do frontend**. Ele segue a mesma regra das telas de leitura: funciona sem JavaScript, o estado de erro volta com os campos preenchidos, e nenhum dado do formulário se perde numa validação recusada.
- Review recém-enviada **não aparece** na ficha. A tela diz isso com todas as letras — quem escreveu precisa entender que está em análise, e não que sumiu.

## 7. Contestação e responsabilidade

### 7.1 Canal de contestação (não é opcional)

Review é o conteúdo com mais exposição jurídica desta fatia — mais do que qualquer coisa de venda que ficou de fora. É texto de terceiro sobre uma empresa identificável, e **sem `vendor` a empresa avaliada não tem para onde reclamar dentro do produto**. O plano §9 resolvia isso com a contestação do vendedor, que depende de uma entidade que não vamos ter.

A mitigação que cabe numa vitrine, e que não precisa de entidade, conta nem painel:

- a **política de reviews** (§6) publica um endereço de contestação e o prazo de resposta;
- a contestação chega por e-mail e o curador decide: manter, editar ou remover, sempre com o motivo;
- **a decisão fica registrada** — `review.moderation_note` e `review.status`, com histórico de quem decidiu e quando. Sem registro, não há como demonstrar que houve análise, que é exatamente o que o Marco Civil art. 19 cobra;
- **review nunca é removida por pedido de quem foi avaliado sem violação de política** (plano §5.2), e isso está escrito na política pública.

Isso não substitui o direito de resposta de verdade — que só chega com o `vendor`. É o mínimo defensável para publicar conteúdo de terceiro, e está aqui declarado como dívida consciente, não como solução completa.

## 8. Qualidade não-funcional

- **A suíte e2e desta fatia escreve** — é a primeira. A constituição §6 exige que suíte de leitura não escreva, então os testes de escrita rodam contra **uma segunda instância no compose** (app + banco próprios, perfil `e2e-escrita`), e não contra a compartilhada. Sem isso, uma review publicada num teste mudaria a nota média que outro teste afirma, com os dois rodando em paralelo.
- Cada teste de escrita usa **e-mail único** (uuid no local-part) e não reaproveita dado, conforme a constituição §6.
- Rate limit por IP no POST público: **[NEEDS CLARIFICATION: janela e limite]**. Sem nenhum, um script enche a fila de moderação; com um agressivo demais, um escritório com NAT único fica de fora.
- E-mail é dado pessoal: nunca em log (a constituição §6 já proíbe logar e-mail completo), nunca em rota pública, e apagado junto com a review não confirmada aos 30 dias.
- Axe sem violação crítica ou séria nas telas novas, como no 002.

## 9. Critérios de aceite

Cada item vira ao menos um teste automatizado (integração para API, Playwright para fluxo, unidade para regra), salvo indicação de "manual".

**Envio e verificação**
- [x] AC-01 `POST /products/{slug}/reviews` válido responde 202, cria em `PENDING_EMAIL` e envia um e-mail com link.
- [x] AC-02 A review em `PENDING_EMAIL` **não** aparece em `GET /products/{slug}/reviews` nem na fila do curador.
- [x] AC-03 Confirmar com o token leva a `PENDING_MODERATION`; o mesmo token uma segunda vez responde 410.
- [x] AC-04 Token expirado (48 h) responde 410, e o reenvio gera um token novo que funciona.
- [x] AC-05 Segundo envio com o mesmo e-mail para o mesmo produto responde **202 igual ao primeiro**, e não cria uma segunda review.
- [x] AC-06 `body` com menos de 80 caracteres e `rating` fora de 1–5 respondem 400 em `problem+json`, nomeando o campo.
- [x] AC-07 Falha no envio do e-mail não perde a review: ela fica em `PENDING_EMAIL` e o reenvio funciona.

**Moderação**
- [x] AC-08 `approve` publica; `reject` sem motivo responde 400; aprovar review não confirmada responde 409.
- [x] AC-09 A fila do admin exige HTTP Basic; sem credencial, 401.
- [x] AC-10 `moderation_note`, `moderated_by` e `author_email` não aparecem em nenhuma rota pública.

**Leitura e agregado**
- [x] AC-11 `GET /products/{slug}/reviews` devolve só `PUBLISHED`, paginado, da mais recente para a mais antiga.
- [x] AC-12 `rating.average` é a média das publicadas com uma casa decimal, e `rating` é `null` quando não há nenhuma.
- [x] AC-13 A média considera reviews dos dois idiomas.
- [x] AC-13a `GET /products/{slug}/reviews` devolve também a proporção de "recomendaria: sim" e a contagem de verificadas — os dois insumos que o ranking do 004 vai consumir.
- [x] AC-14 `GET /products` traz o agregado sem uma consulta por produto (sem N+1).

**Idioma**
- [x] AC-15 Review escrita em `en` aparece na ficha em `/pt-BR` marcada como escrita em inglês.
- [x] AC-16 O e-mail de confirmação sai no idioma da página onde a review foi escrita.

**Telas**
- [x] AC-17 O formulário funciona com JavaScript desabilitado, e um erro de validação volta com os campos preenchidos.
- [x] AC-18 Depois de enviar, a tela diz que a review está em análise e ela não aparece na ficha.
- [x] AC-19 A ficha mostra média e contagem batendo com a API. ~~e distribuição~~ — **corrigido em 2026-08-27, na T11:** este critério contradizia a §6, que corta a distribuição por estrela junto com o filtro e a ordenação ("os três só significam alguma coisa com volume, e com cinco reviews viram enfeite"). A §6 é a decisão mais recente e a que o `tasks.md` já refletia; o AC ficou para trás.
- [x] AC-20 A página de confirmação trata os três casos: sucesso, expirado (com reenvio) e já usado.
- [x] AC-21 Axe não acusa violação crítica ou séria no formulário nem no bloco de reviews.
- [x] AC-21a A página de política de reviews responde nos dois idiomas, entra no `sitemap.xml` e é linkada do bloco de reviews e do formulário.
- [x] AC-21b A política publica o endereço de contestação e a regra de que review só sai por violação de política.
- [x] AC-21c Toda decisão de moderação grava quem decidiu, quando e o motivo — inclusive a de manter depois de uma contestação.

**Infra**
- [x] AC-22 `docker compose up --build` sobe também o Mailpit no perfil de desenvolvimento, e o e2e lê dele o link de confirmação.
- [x] AC-23 Os testes de escrita rodam contra a instância dedicada, e a suíte compartilhada continua sem escrever.

## 10. Decisões já tomadas (não reabrir nesta spec)

- **A plataforma é vitrine** (decisão de 2026-08-27). Mostra o máximo que conseguir sobre cada SaaS e redireciona para o site dele. Vendor, claim, lead, demo, newsletter e cobrança ficam fora enquanto não houver revisão jurídica — e, quando entrarem, entram por essa porta, não por conveniência de implementação.
- **Sem conta de usuário.** Verificação por review, com token de uso único. A 006 substitui, e vai encontrar o e-mail do autor já gravado.
- **Moderação prévia**, não comunitária: nada é publicado sem curador. É o que protege o catálogo enquanto não há reputação de autor.
- **Nota inteira de 1 a 5**, sem meia-estrela e sem sub-notas (suporte, preço, facilidade). Sub-nota multiplica o formulário por quatro e reduz a taxa de resposta — e a 004 pode derivá-las depois se o volume justificar.
- **Domínio gratuito é aceito, sem selo.** Bloquear excluiria a PME que roda no Gmail, que é o público do catálogo.
- **Agregado calculado na consulta**, sem coluna denormalizada.
- **Review não se traduz** (§4.3).
- **`would_recommend` e `verified` existem porque o ranking do 004 precisa deles** (plano §5.1 e §5.2). São campos desta fatia, mas quem os consome é a próxima — e a fórmula já está publicada no plano de produto, então não são um chute.

**O que o plano §4.1 lista como "Reviews v1" e fica de fora, com o motivo:**

| Item do plano | Por que não aqui |
|---|---|
| "Vendedor pode responder" | O próprio plano marca como Fase 2, e `vendor` não existe (001 §2) |
| `review_dispute` (contestação) | Idem: precisa de vendedor identificado |
| Peso 0,5 e fórmula de score | É o 004. Esta fatia grava o insumo, não calcula ranking |
| E-mail automático 14 dias após o clique em "demo" | Depende da tabela `event` e do rastreio de lead, que nenhuma fatia entregou ainda |
| "Conta com 7+ dias" | Não há conta. Ver Q-06 |

## 11. Perguntas resolvidas

Fechadas em 2026-08-27. **Nenhuma pergunta em aberto** — a spec está pronta para virar plano.

| # | Pergunta | Por que importa |
|---|---|---|
| Q-01 | **Rate limit do POST público**: janela e limite por IP | **Fechada em 2026-08-27:** 1 por minuto e 5 por hora, por IP. Barra script sem barrar um escritório atrás de NAT. Passar do limite responde 429, e não 400 — o pedido não está errado, só é cedo demais |
| Q-02 | **Notificar o autor** quando a review é aprovada ou rejeitada? | **Fechada em 2026-08-27:** avisa quando publica, não avisa quando rejeita. Silêncio depois de "em análise" é ruim; e-mail de rejeição abre uma discussão por e-mail que ninguém vai moderar, e a política pública já diz o que é recusado |
| Q-03 | **Retenção do e-mail depois de publicada**: em claro ou HMAC? | **Fechada em 2026-08-27:** em claro, com expurgo das não confirmadas aos 30 dias. A 006 vai precisar vincular a review à conta que se criar com aquele e-mail, e um HMAC tornaria isso impossível sem pedir o e-mail de novo. O dado é minimizado por retenção, não por hash |
| Q-04 | **Nome de exibição**: livre, ou derivado do e-mail (primeiro nome + inicial)? | **Fechada em 2026-08-27:** livre, com o selo de domínio ao lado. O nome não é o que dá credibilidade — o domínio confirmado é. Derivar do e-mail exporia o local-part, que é dado pessoal desnecessário na tela |
| Q-05 | **Unicidade por e-mail ou por domínio?** O plano §5.2 diz domínio; esta spec propõe e-mail (§4.5) | **Fechada em 2026-08-27:** unicidade por e-mail; domínio repetido vira alerta na fila de moderação, não bloqueio. Por domínio travaria o `@gmail.com` inteiro em uma review por produto e barraria o segundo funcionário da mesma empresa — que é justamente quem mais tem o que dizer |
| Q-06 | **Identidade: o plano assume conta, esta spec não.** O plano §5.2 exige "conta com 7+ dias" e o §7.1 põe auth gerenciada dentro do MVP; o roteiro do 001 §11 empurrou identidade para a 006 | **Fechada em 2026-08-27:** sem conta. A plataforma é vitrine, e conta de usuário só existiria para escrever review — não vale um provedor de auth. O substituto da regra dos 7 dias é token por e-mail + moderação prévia + rate limit (Q-01) |

## 12. Próximas specs (para contexto, não implementar)

- **004-ranking-v1** — snapshot de score por categoria, página "como ranqueamos". Consome o agregado desta fatia.
- **005-assistente-rag** — ingestão em `knowledge_chunk` com pgvector, chat com streaming.
- **006-identidade** — substitui o HTTP Basic e o token por e-mail; usuários, magic link, domínio corporativo.
