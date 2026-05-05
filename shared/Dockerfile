# Legent Shared Libraries Base Image
# This image contains all shared modules pre-built
# Services extend this to avoid rebuilding shared dependencies

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy main pom.xml first (parent POM)
COPY pom.xml .

# Copy shared module POMs
COPY shared/legent-common/pom.xml shared/legent-common/
COPY shared/legent-security/pom.xml shared/legent-security/
COPY shared/legent-kafka/pom.xml shared/legent-kafka/
COPY shared/legent-cache/pom.xml shared/legent-cache/
COPY shared/legent-test-support/pom.xml shared/legent-test-support/

# Create minimal parent pom.xml with dependency management
RUN echo '<?xml version="1.0" encoding="UTF-8"?>' > pom.xml.tmp && \
    echo '<project xmlns="http://maven.apache.org/POM/4.0.0"' >> pom.xml.tmp && \
    echo '         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> pom.xml.tmp && \
    echo '         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0' >> pom.xml.tmp && \
    echo '         https://maven.apache.org/xsd/maven-4.0.0.xsd">' >> pom.xml.tmp && \
    echo '    <modelVersion>4.0.0</modelVersion>' >> pom.xml.tmp && \
    echo '    <parent>' >> pom.xml.tmp && \
    echo '        <groupId>org.springframework.boot</groupId>' >> pom.xml.tmp && \
    echo '        <artifactId>spring-boot-starter-parent</artifactId>' >> pom.xml.tmp && \
    echo '        <version>3.2.5</version>' >> pom.xml.tmp && \
    echo '        <relativePath/>' >> pom.xml.tmp && \
    echo '    </parent>' >> pom.xml.tmp && \
    echo '    <groupId>com.legent</groupId>' >> pom.xml.tmp && \
    echo '    <artifactId>legent-email-studio</artifactId>' >> pom.xml.tmp && \
    echo '    <version>1.0.0-SNAPSHOT</version>' >> pom.xml.tmp && \
    echo '    <packaging>pom</packaging>' >> pom.xml.tmp && \
    echo '    <properties>' >> pom.xml.tmp && \
    echo '        <java.version>21</java.version>' >> pom.xml.tmp && \
    echo '        <maven.compiler.source>21</maven.compiler.source>' >> pom.xml.tmp && \
    echo '        <maven.compiler.target>21</maven.compiler.target>' >> pom.xml.tmp && \
    echo '        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>' >> pom.xml.tmp && \
    echo '        <spring-boot.version>3.2.5</spring-boot.version>' >> pom.xml.tmp && \
    echo '        <spring-kafka.version>3.1.4</spring-kafka.version>' >> pom.xml.tmp && \
    echo '        <mapstruct.version>1.5.5.Final</mapstruct.version>' >> pom.xml.tmp && \
    echo '        <lombok.version>1.18.32</lombok.version>' >> pom.xml.tmp && \
    echo '        <jackson.version>2.17.0</jackson.version>' >> pom.xml.tmp && \
    echo '        <ulid.version>5.1.0</ulid.version>' >> pom.xml.tmp && \
    echo '        <testcontainers.version>1.19.7</testcontainers.version>' >> pom.xml.tmp && \
    echo '    </properties>' >> pom.xml.tmp && \
    echo '    <dependencyManagement>' >> pom.xml.tmp && \
    echo '        <dependencies>' >> pom.xml.tmp && \
    echo '            <dependency>' >> pom.xml.tmp && \
    echo '                <groupId>org.springframework.boot</groupId>' >> pom.xml.tmp && \
    echo '                <artifactId>spring-boot-dependencies</artifactId>' >> pom.xml.tmp && \
    echo '                <version>${spring-boot.version}</version>' >> pom.xml.tmp && \
    echo '                <type>pom</type>' >> pom.xml.tmp && \
    echo '                <scope>import</scope>' >> pom.xml.tmp && \
    echo '            </dependency>' >> pom.xml.tmp && \
    echo '        </dependencies>' >> pom.xml.tmp && \
    echo '    </dependencyManagement>' >> pom.xml.tmp && \
    echo '    <modules>' >> pom.xml.tmp && \
    echo '        <module>shared/legent-common</module>' >> pom.xml.tmp && \
    echo '        <module>shared/legent-security</module>' >> pom.xml.tmp && \
    echo '        <module>shared/legent-kafka</module>' >> pom.xml.tmp && \
    echo '        <module>shared/legent-cache</module>' >> pom.xml.tmp && \
    echo '        <module>shared/legent-test-support</module>' >> pom.xml.tmp && \
    echo '    </modules>' >> pom.xml.tmp && \
    echo '</project>' >> pom.xml.tmp && \
    mv pom.xml.tmp pom.xml

# Download all dependencies (cached layer)
RUN mvn dependency:go-offline -B -q

# Now copy source and build shared modules
COPY shared/legent-common/src shared/legent-common/src/
COPY shared/legent-security/src shared/legent-security/src/
COPY shared/legent-kafka/src shared/legent-kafka/src/
COPY shared/legent-cache/src shared/legent-cache/src/
COPY shared/legent-test-support/src shared/legent-test-support/src/

# Build and install shared modules to local Maven repo
RUN mvn clean install -DskipTests -pl shared/legent-common,shared/legent-security,shared/legent-kafka,shared/legent-cache,shared/legent-test-support -am -B -q

# Runtime stage - just the JRE with Maven local repo
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S legent && adduser -S legent -G legent

WORKDIR /app

# Copy the Maven repository with all shared dependencies
COPY --from=builder /root/.m2 /home/legent/.m2
RUN chown -R legent:legent /home/legent/.m2

USER legent

# This image is used as base for service builds
LABEL maintainer="Legent Team" type="shared-base"
