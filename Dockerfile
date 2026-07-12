# syntax=docker/dockerfile:1

# ---- Dependencies (shared) ---------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS deps
WORKDIR /workspace

COPY pom.xml .
COPY acruet-core/pom.xml acruet-core/
COPY acruet-user-war/pom.xml acruet-user-war/
COPY acruet-admin-war/pom.xml acruet-admin-war/
RUN mvn -q -B dependency:go-offline

# ---- Build (both WARs) -------------------------------------------------------
FROM deps AS build
COPY acruet-core acruet-core
COPY acruet-user-war acruet-user-war
COPY acruet-admin-war acruet-admin-war
RUN mvn -q -B clean package

# ---- User runtime ------------------------------------------------------------
FROM tomcat:10.1-jre17-temurin AS user
LABEL org.opencontainers.image.title="acruet-user" \
      org.opencontainers.image.description="User-facing a-cruet Tomcat application"

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /workspace/acruet-user-war/target/acruet-user.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/health || exit 1

CMD ["catalina.sh", "run"]

# ---- Admin runtime -----------------------------------------------------------
FROM tomcat:10.1-jre17-temurin AS admin
LABEL org.opencontainers.image.title="acruet-admin" \
      org.opencontainers.image.description="Administrator a-cruet Tomcat application"

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /workspace/acruet-admin-war/target/acruet-admin.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/health || exit 1

CMD ["catalina.sh", "run"]
