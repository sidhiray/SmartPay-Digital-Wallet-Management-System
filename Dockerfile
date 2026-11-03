
# Stage 1: Build application
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src/ src/

RUN ./mvnw -B package -DskipTests

# Stage 2: Run application
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system smartpay && \
    useradd --system --gid smartpay smartpay

COPY --from=builder /app/target/*.jar app.jar

USER smartpay

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
