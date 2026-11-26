# Build the React client first.
FROM node:20-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Package the Spring Boot API and serve the frontend from the same origin.
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
COPY --from=frontend-build /workspace/frontend/dist/ /tmp/frontend-dist/
RUN mkdir -p backend/src/main/resources/static \
    && cp -R /tmp/frontend-dist/. backend/src/main/resources/static/ \
    && mvn -B -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/access-request-0.0.1.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
