# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O pom vem sozinho primeiro para que a resolucao de dependencias vire uma
# camada propria, reaproveitada enquanto o pom nao mudar.
COPY pom.xml ./
# As flags de retentativa nao sao zelo excessivo: este passo baixa a arvore
# inteira de dependencias e ja falhou no CI por um erro transitorio do Maven
# Central. Sem elas, uma oscilacao de rede derruba o build e ensina a
# reexecutar o CI em vez de olhar o que quebrou.
ARG MAVEN_OPTS_REDE="-Dmaven.wagon.http.retryHandler.count=3 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"
RUN --mount=type=cache,target=/root/.m2 mvn -B $MAVEN_OPTS_REDE dependency:go-offline

COPY src ./src
# Testes nao rodam aqui: os de integracao precisam de Docker (Testcontainers) e
# nao ha Docker dentro do build. Quem roda a suite e o `./mvnw verify`.
# Spotless tambem fica de fora - formatacao e verificada no build local e na CI.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B $MAVEN_OPTS_REDE -DskipTests -Dspotless.check.skip=true package && \
    mv target/marketplace-saas-*.jar /build/app.jar

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Usuario nao-root dedicado (constituicao: nada roda como root em container).
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --create-home --home-dir /app app

# curl entra so para o healthcheck do compose. A imagem jre-jammy nao traz curl
# nem wget, e sem healthcheck o `docker compose up --wait` da o container como
# pronto assim que o processo sobe - antes de o Spring aceitar requisicao. Quem
# depende disso e o e2e do frontend, que subia contra uma API ainda bootando.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app

EXPOSE 8080

# MaxRAMPercentage faz a JVM respeitar o limite de memoria do container em vez
# de enxergar a RAM do host inteiro.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
