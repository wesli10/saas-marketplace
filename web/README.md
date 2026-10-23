# `web/` — frontend do catálogo

Next.js (App Router, SSR) que consome a API pública do catálogo. A fatia está
descrita em `specs/002-frontend-catalogo/`; as regras que valem aqui estão na
constituição (`CLAUDE.md`), seções 3, 4, 6 e 8.

## Comandos

| Comando                  | O que faz                                                                                                             |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `npm run dev`            | Sobe em modo desenvolvimento. Precisa da API de pé — veja o `docker compose` da raiz                                  |
| `npm run verify`         | Formato, lint, tipos e testes de unidade. É o gate de toda tarefa                                                     |
| `npm run build`          | Build de produção (`output: standalone`)                                                                              |
| `npm test`               | Só os testes de unidade e componente (Vitest)                                                                         |
| `npm run e2e:smoke`      | O caminho crítico, em Chromium. Roda em todo push no CI, com orçamento de 5 min                                       |
| `npm run e2e`            | A suíte inteira, nos cinco projetos. Sob demanda                                                                      |
| `npm run e2e:isolamento` | A suíte na topologia invertida (`--workers=1 --repeat-each=2`), substituto do embaralhamento que o Playwright não tem |

Os testes de ponta a ponta falam com a API de verdade: suba o compose da raiz
antes (`docker compose up --build -d --wait`). Não há mock de API na suíte.

O caminho normal para rodar tudo é o `docker compose up --build` da raiz, que
sobe banco, API e este frontend juntos.
