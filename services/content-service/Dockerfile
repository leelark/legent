FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 legent \
    && adduser --system --uid 1001 legent

COPY target/*-SNAPSHOT.jar app.jar

RUN chown legent:legent app.jar

USER legent

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
