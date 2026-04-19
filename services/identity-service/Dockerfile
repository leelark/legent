FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup --system --gid 1001 legent \
    && adduser --system --uid 1001 legent

COPY target/*-SNAPSHOT.jar app.jar

RUN chown legent:legent app.jar

USER legent

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
