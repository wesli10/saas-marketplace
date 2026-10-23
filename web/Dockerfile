# syntax=docker/dockerfile:1

# ---------- dependencias ----------
# As dependencias vem numa camada propria, reaproveitada enquanto o
# package-lock nao mudar - mesma ideia do `dependency:go-offline` do backend.
FROM node:22-alpine AS deps
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /build
COPY --from=deps /build/node_modules ./node_modules
COPY . .
# Testes nao rodam aqui: unidade e Vitest no `npm run verify`, e o e2e precisa
# do compose de pe, que nao existe dentro do build.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---------- runtime ----------
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Usuario nao-root dedicado (constituicao: nada roda como root em container).
RUN addgroup --system --gid 1001 app \
 && adduser --system --uid 1001 --ingroup app app

# O standalone traz o server.js e so as dependencias que ele usa. Os estaticos
# ficam de fora do bundle de propósito e precisam ser copiados a parte.
COPY --from=build --chown=app:app /build/.next/standalone ./
COPY --from=build --chown=app:app /build/.next/static ./.next/static

USER app
EXPOSE 3000

CMD ["node", "server.js"]
