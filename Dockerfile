FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN apt-get update \
    && apt-get install -y maven \
    && mvn clean package -DskipTests \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 10000

CMD ["sh", "-c", "java -jar target/mock-interview-bot-1.0.0.jar"]