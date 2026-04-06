FROM eclipse-temurin:21-jre-alpine
COPY target/fm-admin*.jar /opt/finmates/app.jar
ENTRYPOINT ["java", "-jar", "/opt/finmates/app.jar"]
