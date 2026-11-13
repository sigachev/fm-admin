FROM eclipse-temurin:21-jre-alpine
# Pin the in-cluster Keycloak self-signed certificate (ADDS to the default cacerts; never trust-all).
# Keep the PEM in the image so KeycloakTrustAnchorLogger can report it at startup.
COPY cert/self-signed/finmates.cert /opt/finmates/finmates.cert
RUN keytool -importcert -noprompt -trustcacerts -alias finmates-keycloak -cacerts -storepass changeit -file /opt/finmates/finmates.cert
COPY target/fm-admin*.jar /opt/finmates/app.jar
ENTRYPOINT ["java", "-jar", "/opt/finmates/app.jar"]
