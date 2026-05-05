FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S legent && adduser -S legent -G legent

COPY target/deliverability-service-1.0.0-SNAPSHOT.jar app.jar

RUN chown legent:legent app.jar

USER legent

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
