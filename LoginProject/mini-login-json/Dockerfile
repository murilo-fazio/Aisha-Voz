# ==========================================================================
# Imagem da aplicacao web/backend (Spring Boot 4 + Java 25).
#
# Build multi-stage: o JDK e o Maven so existem no estagio de build. A imagem
# final carrega apenas o JRE e o jar, e roda como usuario nao-root.
#
# Contexto de build esperado: ./LoginProject/mini-login-json
# ==========================================================================

# --------------------------------------------------------------------------
# Estagio 1 — build
# --------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# O pom.xml vem sozinho e primeiro de proposito: enquanto ele nao mudar, o
# Docker reaproveita a camada de dependencias e nao baixa a internet de novo
# a cada alteracao de codigo.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Agora sim o codigo, que muda a toda hora.
COPY src ./src

# Testes nao rodam aqui: a imagem e artefato de entrega, nao pipeline de CI.
# A suite roda no './mvnw test' do desenvolvedor e no CI.
RUN mvn -B clean package -DskipTests

# --------------------------------------------------------------------------
# Estagio 2 — runtime
# --------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# Usuario nao-root: se a aplicacao for comprometida, o invasor nao e root
# dentro do container.
RUN groupadd --system --gid 1001 aisha \
 && useradd --system --uid 1001 --gid aisha --home-dir /app --shell /usr/sbin/nologin aisha

WORKDIR /app

RUN chown -R aisha:aisha /app

# So o jar final atravessa os estagios. O curinga casa apenas o jar
# reempacotado pelo spring-boot-maven-plugin (o original vira .jar.original).
COPY --from=build --chown=aisha:aisha /build/target/*.jar /app/app.jar

USER aisha

# Perfil padrao da imagem. O compose pode sobrescrever via ambiente.
ENV SPRING_PROFILES_ACTIVE=docker

# Porta interna fixa. Quem escolhe a porta do host e o compose (APP_PORT).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
