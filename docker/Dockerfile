FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
COPY tools/brandpay-test ./tools/brandpay-test
RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG SPRING_PROFILES_ACTIVE=dev
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
