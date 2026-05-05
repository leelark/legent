# ─── Foundation Service Dockerfile ───
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Legent Team" service="foundation-service"

RUN addgroup -S legent && adduser -S legent -G legent

WORKDIR /app

# Copy only the specific JAR (smaller layer, deterministic)
COPY target/foundation-service-1.0.0-SNAPSHOT.jar app.jar

RUN chown -R legent:legent /app
USER legent

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
