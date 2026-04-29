FROM eclipse-temurin:21-jdk AS build

ARG MILL_VERSION=1.0.6
WORKDIR /workspace

# Install Mill from Maven Central (official bootstrap script URL).
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && curl -fsSL "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/${MILL_VERSION}/mill-dist-${MILL_VERSION}-mill.sh" -o /usr/local/bin/mill \
  && chmod +x /usr/local/bin/mill

COPY build.sc ./
COPY shared ./shared
COPY frontend ./frontend
COPY backend ./backend

RUN mill backend.assembly

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app
COPY --from=build /workspace/out/backend/assembly.dest/out.jar /app/app.jar

EXPOSE 8080

ENV PROJECT_ID=project_id
ENV API_KEY=api_key

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
