# Marketplace bilíngue de SaaS — catálogo core

Diretório bilíngue (pt-BR / en) de soluções SaaS para PMEs: árvore de categorias, busca full-text com fallback por similaridade, ficha de produto, comparação lado a lado e as rotas de curadoria protegidas por HTTP Basic.

São três fatias entregues: **001-catalogo-core** (a API e a curadoria), **002-frontend-catalogo** (o site que a consome, em `web/`) e **003-reviews** (avaliações verificadas por e-mail, com moderação prévia). O que cada uma entrega, o porquê de cada decisão e o histórico de desvios estão em [`specs/001-catalogo-core/`](specs/001-catalogo-core/), [`specs/002-frontend-catalogo/`](specs/002-frontend-catalogo/) e [`specs/003-reviews/`](specs/003-reviews/); as regras invariantes do projeto, em [`CLAUDE.md`](CLAUDE.md).

---

## Pré-requisitos

| Ferramenta | Versão | Por quê |
|---|---|---|
| **Docker** | com `docker compose` | Sobe app + Postgres. **Obrigatório também para os testes**: a suíte usa Testcontainers com Postgres real, nunca H2 (constituição §7.3) |
| Java | 21 | Só para rodar fora do compose. O `./mvnw` baixa o resto |

Não é preciso instalar Maven nem Postgres na máquina.

---

## Como rodar

```bash
cp .env.example .env      # ajuste as senhas antes de subir
docker compose up --build
```

O `.env` é obrigatório: o compose recusa subir sem `DB_USER`, `DB_PASSWORD`, `ADMIN_USER` e `ADMIN_PASSWORD`, e a aplicação **não sobe** sem credencial de curadoria — de propósito, para não existir senha padrão esquecida em produção.

Para subir já com o catálogo populado, ponha `SEED_ENABLED=true` no `.env`.

Quando terminar:

```bash
docker compose down       # preserva o volume do banco
docker compose down -v    # apaga o volume também
```

### Endereços

| O quê | Onde |
|---|---|
| **Site** | http://localhost:3000 (a raiz redireciona para o idioma do navegador) |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Documento OpenAPI | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| **Mailpit** (caixa de entrada local) | http://localhost:8025 — só com `--profile dev`. Ver [Avaliações](#avaliações) |

---

## Como testar

```bash
./mvnw verify
```

Roda Spotless (formatação), os testes unitários (`*Test`), os de integração (`*IT`) contra um Postgres de verdade e gera o relatório de cobertura em `target/site/jacoco/index.html`.

O Docker precisa estar rodando: cada execução sobe um container `pgvector/pgvector:pg16`. A primeira vez baixa a imagem e demora mais.

Se o Spotless reclamar de formatação:

```bash
./mvnw spotless:apply
```

---

### Testes do frontend

```bash
cd web
npm run verify        # unidade e componente (Vitest) + as varreduras
npm run e2e           # a suíte completa, contra o compose de pé
npm run e2e:smoke     # só os cinco casos de smoke (~2 s)
npm run e2e:isolamento # a suíte numa topologia invertida: 1 worker, cada teste duas vezes
```

O e2e roda contra o **artefato de produção** subido pelo compose, e não contra o `next dev`. Antes de rodar:

```bash
SEED_ENABLED=true docker compose --profile e2e up --build -d --wait
```

O `--profile e2e` sobe também a instância com a API apontada para um endereço morto, que é o alvo dos testes de resiliência. Sem ela, três testes falham com conexão recusada.

### Suíte completa de ponta a ponta

A **smoke** roda sozinha, em todo push (job `web` do CI). A **suíte completa** —
sete projetos do Playwright, incluindo WebKit, viewport mobile, os dois runs sem
JavaScript e a instância que escreve — roda ao promover para `homol`:

```
feature/*  →  dev  →  homol  →  main
   smoke      smoke    FULL     release
```

Promover para `homol` é o gatilho: não há passo manual a lembrar, e `main` só
recebe o que passou por lá. A smoke cobre o caminho crítico e nada mais; ela
existe para dar resposta rápida no push, não para autorizar release.

O disparo manual (Actions → *e2e full* → *Run workflow*) também existe, mas o
GitHub só o exibe quando o workflow está no branch padrão — ou seja, ele só
aparece depois que a fatia chega na `main`. No dia a dia, use a `homol`.

A suíte completa sobe **dois perfis** do compose: `e2e` (instância sem API, para
os testes de resiliência) e `e2e-escrita` (banco, API e site próprios, mais o
Mailpit, para os testes que escrevem). Nenhum dos dois entra no CI de push: a
smoke não os toca, e subir sete containers a mais por push custaria minutos sem
cobrir nada novo.

## O site (`web/`)

Next.js com App Router, **renderizado no servidor**: a primeira resposta é HTML, porque o consumidor principal do catálogo é buscador. Não é SPA, não fala com o banco, não tem BFF — consome apenas `/api/v1/**`, e a partir do servidor.

O que ele entrega:

| Tela | Rota (pt-BR · en) |
|---|---|
| Home com as categorias e a busca | `/pt-BR` · `/en` |
| Categoria com filtros e paginação | `/pt-BR/categorias/{slug}` · `/en/categories/{slug}` |
| Busca (mesma lista, outra porta) | `/pt-BR/buscar?q=` · `/en/search?q=` |
| Ficha do produto | `/pt-BR/produtos/{slug}` · `/en/products/{slug}` |
| Comparação de 2 a 3 produtos | `/pt-BR/comparar?slugs=a,b` · `/en/compare?slugs=a,b` |
| Descoberta | `/sitemap.xml`, `/robots.txt` |

Três regras que valem para todas elas:

- **O estado vive na URL.** Filtro, página, busca e seleção de comparação são parâmetros — compartilháveis, e o botão "voltar" devolve o que era. Não há gerenciador de estado.
- **Funciona sem JavaScript.** Filtros são `<form method="GET">`, paginação e comparação são links. Com JS a experiência melhora (o filtro submete sozinho); sem ele, nada quebra. Há um projeto do Playwright inteiro rodando com o motor desligado.
- **O idioma está no caminho, sempre.** `/` redireciona por `Accept-Language`; idioma desconhecido é 404, não um fallback silencioso para português.

Para mexer só no frontend, com a API do compose de pé:

```bash
cd web
npm ci
npm run dev        # http://localhost:3000, falando com a API em localhost:8080
npm run verify     # formato, lint, tipos, as três varreduras e os testes de unidade
```

O `verify` é o gate do frontend, equivalente ao `./mvnw verify` do backend. Ele inclui três varreduras próprias: `data-testid` em elemento interativo, independência dos testes e2e, e vazamento de dado do seed dentro de `web/`.

Para rodar o `next dev` fora do compose, preencha `CORS_ALLOWED_ORIGINS=http://localhost:3000` no `.env` — em produção ela fica vazia de propósito.

---

## Avaliações

A fatia **003-reviews** deixa quem usa o produto falar sobre ele, sem criar conta. O fluxo inteiro, do que a pessoa escreve até o que aparece na ficha:

1. **Escreve** em `/pt-BR/produtos/{slug}/avaliar` — nota de 1 a 5, "recomendaria?", resumo, texto (mínimo de 80 caracteres), nome de exibição e e-mail de trabalho. O formulário **funciona sem JavaScript**.
2. **Confirma o e-mail** pelo link que chega na caixa de entrada. O link vale 48 h e serve uma vez; o token vai no banco só como SHA-256.
3. **A curadoria lê** antes de publicar. Nada aparece automaticamente: `GET /api/v1/admin/reviews` lista a fila, e `approve` / `reject` decidem — sempre gravando quem decidiu, quando e por quê.
4. **A avaliação aparece** na ficha, com a nota do produto no card, na ficha e na comparação. Quem escreveu recebe um aviso de que saiu.

O que dá lastro ao selo de verificado está publicado em `/pt-BR/politica-de-reviews`, junto com o endereço de contestação e o prazo de resposta.

### A caixa de entrada local

Em desenvolvimento nada sai da máquina: o compose sobe um **Mailpit** no perfil `dev`, e é para ele que a aplicação envia.

```bash
SEED_ENABLED=true docker compose --profile dev up --build -d --wait
```

Abra http://localhost:8025 e escreva uma avaliação — a mensagem cai ali, com o link de confirmação, no idioma da página onde ela foi escrita.

Sem o perfil, `docker compose up` continua subindo só `db + app + web`. A aplicação **não quebra** sem o Mailpit: falha de envio é registrada no log e a avaliação fica aguardando confirmação, com o reenvio como caminho de volta.

> O `/actuator/health` **não** depende do SMTP, de propósito. O `spring-boot-starter-mail` liga um health indicator por conta própria, e com ele um servidor de e-mail fora do ar derrubaria o healthcheck do compose e o CI enquanto o catálogo inteiro continuaria respondendo.

### A instância que escreve

A suíte e2e compartilhada **não escreve** (constituição §6): uma avaliação publicada mudaria a nota que outro teste afirma, com os dois em paralelo. Os testes de escrita rodam contra uma segunda instância inteira — banco, API e site próprios:

```bash
SEED_ENABLED=true docker compose --profile e2e-escrita up --build -d --wait
cd web && npm run e2e -- --project=escrita --project=escrita-no-js
```

Ela sobe na 3002 (site) e na 8081 (API), com o Mailpit junto, e o banco em `tmpfs` — nasce limpa a cada `up`.

---

## Variáveis de ambiente

| Variável | Padrão | Para que serve |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://db:5432/saasfinder` | Dentro do compose o host é `db`; fora dele, `localhost` |
| `DB_USER` / `DB_PASSWORD` | — | Obrigatórias |
| `DB_PORT` | `5432` | Porta do Postgres exposta no host |
| `APP_PORT` | `8080` | Porta da aplicação no host |
| `ADMIN_USER` / `ADMIN_PASSWORD` | — | Credencial de `/api/v1/admin/**`. **Sem elas a aplicação não sobe** |
| `SEED_ENABLED` | `false` | `true` carrega o catálogo inicial quando o banco está vazio |
| `APP_DEFAULT_LOCALE` | `pt-BR` | Idioma usado quando a requisição não pede nenhum |
| `WEB_PORT` | `3000` | Porta do site no host |
| `API_BASE_URL` | `http://app:8080` | Endereço **interno** da API. Só o servidor do Next o usa; ele nunca chega ao navegador (AC-24) |
| `SITE_URL` | `http://localhost:3000` | URL pública do site. Ao contrário da de cima, esta **é** pública: sai nas canônicas, no `hreflang` e no `sitemap.xml`. Em produção, é obrigatória na prática — sem ela o site publica canônicas apontando para `localhost` |
| `WEB_SEM_API_PORT` | `3001` | Instância com a API apontada para um endereço morto, usada só pelos testes de resiliência (perfil `e2e`) |
| `CORS_ALLOWED_ORIGINS` | vazio | Origens liberadas para chamada de navegador. Vazio em produção de propósito: a renderização é no servidor, então nenhuma página chama a API pelo navegador |
| `MAIL_HOST` / `MAIL_PORT` | `mailpit` / `1025` | SMTP do fluxo de confirmação de avaliação. No compose apontam para o Mailpit; em produção, para o SMTP de verdade |
| `MAIL_USER` / `MAIL_PASSWORD` | vazios | Credencial do SMTP. Vazias no Mailpit, que aceita qualquer coisa |
| `MAIL_FROM` | `reviews@localhost` | Remetente das mensagens. Não é segredo, e é o que identifica quem escreveu |
| `RATE_LIMIT_POR_MINUTO` / `RATE_LIMIT_POR_HORA` | `1` / `5` | Limite do POST público de avaliação e do reenvio, por IP (spec 003, Q-01) |
| `TRUST_FORWARDED_FOR` | `false` | Ler `X-Forwarded-For` **só** com proxy reverso de verdade na frente. Sem ele, qualquer um forja o header e contorna o limite |
| `REVIEWS_CONTATO` | `contestacao@example.com` | Endereço publicado na política para contestar uma avaliação. **É um compromisso público — preencha antes de publicar.** O padrão usa `example.com`, reservado pela IANA para documentação |
| `REVIEWS_PRAZO_DIAS` | `10` | Prazo de resposta a uma contestação, em dias úteis. Também um compromisso público |
| `REVIEWS_EXPURGO_CRON` | `0 30 3 * * *` | Quando roda o expurgo das avaliações que nunca foram confirmadas (30 dias) |
| `MAILPIT_SMTP_PORT` / `MAILPIT_UI_PORT` | `1025` / `8025` | Portas do Mailpit no host |
| `WEB_ESCRITA_PORT` / `APP_ESCRITA_PORT` | `3002` / `8081` | Instância com banco próprio, alvo dos testes que **escrevem** (perfil `e2e-escrita`) |

Segredo nenhum vai para o repositório: o `.env` está no `.gitignore` e o `.env.example` só traz placeholders.

---

## O que a API entrega

**Público (sem autenticação)**

| Método | Rota | O quê |
|---|---|---|
| GET | `/api/v1/categories` | Árvore de categorias com contagem de publicados (inclui subcategorias) |
| GET | `/api/v1/categories/{slug}` | Uma categoria; o slug vale nos dois idiomas |
| GET | `/api/v1/products` | Lista e busca, com filtros combináveis e paginação |
| GET | `/api/v1/products/{slug}` | Ficha completa, resolvida no idioma pedido |
| GET | `/api/v1/products/compare?slugs=a,b,c` | 2 a 3 fichas lado a lado, na ordem pedida |
| GET | `/api/v1/integrations` | Integrações com contagem de produtos |
| GET | `/api/v1/products/{slug}/reviews` | Avaliações publicadas, com a nota agregada do produto |
| POST | `/api/v1/products/{slug}/reviews` | Envia uma avaliação. **202 sempre** que a entrada é válida — inclusive quando aquele e-mail já avaliou aquele produto |
| POST | `/api/v1/reviews/confirm` | Confirma o e-mail. 410 se o link foi usado, expirou ou não existe |
| POST | `/api/v1/reviews/resend` | Manda um link novo. 202 sempre, pelo mesmo motivo do envio |

**Curadoria (HTTP Basic)** — CRUD de categorias, integrações, tags e produtos, conteúdo curado por idioma e o ciclo `publish` / `unpublish` / `archive`, sob `/api/v1/admin`. Mais a fila de avaliações: `GET /api/v1/admin/reviews`, `approve`, `reject` (motivo obrigatório) e `note`, que registra a decisão de **manter** depois de uma contestação.

**Resposta uniforme, de propósito.** As duas rotas públicas de escrita respondem igual tendo ou não havido efeito. Dizer "você já avaliou" contaria, a quem apenas chutou um endereço, que aquela pessoa avaliou aquele produto.

**Idioma.** Via header `Accept-Language` ou query `?lang=`; a query vence. Aceita `pt-BR` e `en`. `?lang=fr` explícito devolve 400; um `Accept-Language` só com idiomas não suportados cai no padrão sem erro, como manda a negociação de conteúdo HTTP. Toda resposta traz `Content-Language`.

**Busca.** Full-text por dicionário do idioma, insensível a acento, ordenada por `ts_rank_cd`. Se o full-text não achar nada, uma segunda consulta tenta similaridade no nome. O campo `query.matched_by` (`FULL_TEXT` | `TRIGRAM` | `NONE`) diz por qual caminho o resultado veio.

**Erros.** RFC 9457 (`application/problem+json`), com `errors[]` nomeando os campos em `snake_case`.

---

## Verificação manual

Os passos abaixo foram executados nas datas indicadas e os números são os observados. Os demais critérios de aceite têm teste automatizado — a matriz AC → tarefa está nos `tasks.md` de cada fatia ([001](specs/001-catalogo-core/tasks.md), [002](specs/002-frontend-catalogo/tasks.md)).

### AC-01 — sobe do zero (001, 002 e 003)

```bash
docker compose down -v
SEED_ENABLED=true docker compose --profile e2e up --build -d --wait
curl -s localhost:8080/actuator/health                          # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' localhost:3000         # 307
curl -sL -o /dev/null -w '%{http_code}\n' localhost:3000        # 200
```

**API, 2026-08-26:** health `UP` em **49 s** desde o início do build, contra o limite de 90 s. Container como não-root (`docker compose exec app id -u` → `1001`).

**Stack inteira, 2026-08-27:** `down -v` seguido de `up --build --wait` levou **1 min 57 s** com o cache de camadas do Docker quente, e os quatro serviços ficaram saudáveis. O site como não-root (`docker compose exec web id -u` → `1001`), 20 produtos no seed e 52 URLs no `sitemap.xml`.

**Stack inteira com as três fatias, 2026-08-27 (spec 003):** `down -v` seguido de `up --build -d --wait` com **os três perfis** (`dev`, `e2e`, `e2e-escrita`) levou **51 s** com o cache de camadas quente, e os **oito** serviços subiram saudáveis. Health `UP`, raiz em `307` (`200` seguindo o redirect), 20 produtos no seed, **54 URLs** no `sitemap.xml` (as duas novas são a política, uma por idioma) e o site como não-root (`1001`).

> A raiz responde **307**, e não 200: ela redireciona para o idioma do navegador (AC-03). Quem verifica AC-01 com `curl` precisa seguir o redirect (`-L`) ou pedir `/pt-BR` direto — 200 na raiz seria o sintoma de o prefixo de idioma ter sumido.

### Demo de sucesso (spec §1) — a mesma busca nos dois idiomas

```bash
curl -sG localhost:8080/api/v1/products --data-urlencode "q=atendimento"      --data-urlencode "lang=pt-BR"
curl -sG localhost:8080/api/v1/products --data-urlencode "q=customer support" --data-urlencode "lang=en"
```

Ambas devolvem `matched_by: FULL_TEXT` e praticamente o mesmo conjunto: 9 produtos em pt-BR e 8 em en. **A diferença é real e esperada** — o único item a mais em português é o `twilio-whatsapp`, cujo texto em pt usa "janela de **atendimento**" (o termo que a Meta usa em português) enquanto o texto em inglês diz "service window". Os textos dos produtos são paralelos, não traduções literais, então a busca sobre eles não é uma bijeção. O AC-10, que exige o mesmo conjunto, é verificado com fixtures controladas em `ProdutoBuscaIT`.

### Demo da spec 003 §1 — uma avaliação, do envio à nota na ficha

Executada em **2026-08-27**, na stack recém-subida do zero, e leva **menos de um minuto**:

```bash
set -a; . ./.env; set +a

# 1. envia (202 sempre que a entrada é válida)
curl -s -X POST localhost:8080/api/v1/products/zapier/reviews \
  -H 'Content-Type: application/json' -H 'Accept-Language: pt-BR' \
  -d '{"rating":4,"would_recommend":true,"title":"Automatizou o financeiro",
       "body":"Ligamos o financeiro ao CRM em uma tarde e paramos de digitar boleto na mao. O plano gratuito ja resolvia dois fluxos.",
       "author_name":"Ana Ribeiro","author_email":"ana@empresa-da-ana.com.br"}'

# 2. pega o link no Mailpit (http://localhost:8025) e confirma
curl -s -X POST localhost:8080/api/v1/reviews/confirm \
  -H 'Content-Type: application/json' -d '{"token":"<o token do link>"}'

# 3. o curador aprova
curl -s -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  'localhost:8080/api/v1/admin/reviews?status=PENDING_MODERATION&product=zapier'
curl -s -X POST -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  localhost:8080/api/v1/admin/reviews/<id>/approve
```

**Observado:** `202` no envio, com a mensagem *"Confirme sua avaliacao de Zapier"* caindo no Mailpit em português — o idioma da página onde a avaliação foi escrita. `200` na confirmação, `200` na aprovação, e um segundo e-mail (*"Sua avaliacao de Zapier esta publicada"*) avisando quem escreveu. Depois disso, `GET /api/v1/products/zapier` passou a trazer `rating: {average: 4.0, count: 1, recommend_ratio: 1.0, verified_count: 1}`, o mesmo agregado apareceu no card da listagem, e a ficha renderizou **4,0** em `/pt-BR` e **4.0** em `/en` — mesmo número, separador decimal de cada idioma —, com o selo `empresa-da-ana.com.br` ao lado da avaliação.

Nenhuma linha de log trouxe o e-mail completo nem o token: o que sai é `e-mail enviado para dominio empresa-da-ana.com.br`.

### AC-12 — busca tolerante a erro de digitação

```bash
curl -sG localhost:8080/api/v1/products --data-urlencode "q=zendes"
```

Devolve `zendesk` com `matched_by: TRIGRAM`.

### AC-17 e AC-23 e AC-24 — segurança e documentação

```bash
curl -so /dev/null -w "%{http_code}\n" localhost:8080/api/v1/admin/products   # 401
curl -so /dev/null -w "%{http_code}\n" localhost:8080/swagger-ui/index.html   # 200
curl -so /dev/null -w "%{http_code}\n" localhost:8080/actuator/env            # 404
```

Na Swagger UI, `GET /products` executa pela própria interface; as rotas de curadoria pedem credencial no botão **Authorize**.

---

## Notas de operação

**`CREATE EXTENSION` exige privilégio.** A migração `V1` cria `unaccent`, `pg_trgm` e `vector`. Em Postgres gerenciado (RDS, Cloud SQL, Neon), o usuário da aplicação normalmente **não** tem permissão para isso: peça a um superusuário para criar as três extensões no banco antes do primeiro deploy, ou use um usuário com o privilégio necessário só na migração. A imagem `pgvector/pgvector:pg16` do compose já vem com as três disponíveis.

**Migrações são imutáveis.** Uma migração Flyway commitada nunca é editada; correção vira migração nova (constituição §5). Foi assim que a `V6` alargou `price_min` para quatro casas, quando a coleta de dados reais mostrou que produto cobrado por unidade não cabia em duas.

**O seed é opt-in e idempotente.** Com `SEED_ENABLED=true`, o loader popula o catálogo **apenas** se ele estiver vazio, protegido por advisory lock e por checagem das quatro tabelas que ele escreve. Reiniciar não duplica nada.

**Dados do seed.** Os 20 produtos são reais e cada um carrega em `source_note` a URL da página de pricing consultada e a data da coleta. Onde a página não publica o dado, o campo fica nulo — nunca preenchido de memória. `source_note` nunca aparece nas rotas públicas.
