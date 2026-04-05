#
# Package stage
#
FROM eclipse-temurin:21-jre-alpine

WORKDIR /opt/finmates

ARG CERT="finmates.cert"
COPY cert/self-signed/$CERT /opt/finmates/

RUN keytool -importcert -alias finmates -storepass changeit -storetype PKCS12 -keystore $JAVA_HOME/lib/security/cacerts -trustcacerts -noprompt -file $CERT

COPY /target/finmates-admin-*.jar /opt/finmates/app.jar
ENTRYPOINT ["java","-jar","app.jar"]
