FROM public.ecr.aws/docker/library/eclipse-temurin:17-jdk-jammy

RUN apt-get update \
    && apt-get install -y dmidecode \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY app/build/libs/app-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]