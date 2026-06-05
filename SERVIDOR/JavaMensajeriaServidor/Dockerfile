FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/JavaMensajeriaServidor-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
