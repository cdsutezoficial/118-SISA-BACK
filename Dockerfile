# syntax=docker/dockerfile:1

# ──────────────────────────────────────────────
# Stage 1: build
# Java 21 (pom.xml java.version) sobre Maven 3.9.
# El wrapper del repo apunta a Maven 3.9.16; se usa el mvn de la imagen
# para no descargar la distribucion en cada build.
# ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Capa aparte: mientras el pom no cambie, no se vuelven a bajar las
# dependencias. Solo se corre maven en el paquete final.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package \
 && cp target/core-*.jar target/app.jar

# ──────────────────────────────────────────────
# Stage 2: runtime
# Solo el JRE y el jar. Corre como usuario sin privilegios.
# ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system sisa \
 && useradd --system --gid sisa --no-create-home --shell /usr/sbin/nologin sisa

COPY --from=build --chown=sisa:sisa /build/target/app.jar /app/app.jar

USER sisa

EXPOSE 8080

# El puerto interno del contenedor es fijo (8080). Lo que cambia por
# ambiente es el puerto del host, via API_PORT en el .env.
ENV SERVER_PORT=8080 \
    TZ=America/Mexico_City \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

# No hay actuator, asi que el healthcheck comprueba que el puerto
# escucha. No requiere instalar nada en la imagen.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c "exec 3<>/dev/tcp/127.0.0.1/8080"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
