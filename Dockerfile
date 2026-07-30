FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew .
COPY gradlew.bat .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

ARG GPR_USER
ARG GPR_TOKEN

ENV GPR_USER=$GPR_USER
ENV GPR_TOKEN=$GPR_TOKEN

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java","-jar","/app/app.jar"]