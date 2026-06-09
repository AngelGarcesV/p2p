FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY JavaMensajeriaComunicacion ./JavaMensajeriaComunicacion
RUN mvn -f JavaMensajeriaComunicacion/pom.xml install -DskipTests

COPY JavaMensajeriaServidor ./JavaMensajeriaServidor
RUN mvn -f JavaMensajeriaServidor/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/JavaMensajeriaServidor/target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=9090"]
