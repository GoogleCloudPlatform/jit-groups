# Custom docker file for https://github.com/GoogleCloudPlatform/jit-access.git that uses CORP CERTS and CORP proxy

#
# Build stage.
#
FROM --platform=linux/amd64 maven:3.8.7-eclipse-temurin-17 AS build
WORKDIR /app
USER root

# Load internal certs PEM replace (https://certstore.corp.com.au/corp_ca_cert_chain.pem) with your certs
RUN curl -fL -o /usr/local/share/ca-certificates/corp_ca_cert_chain.pem https://certstore.corp.com.au/corp_ca_cert_chain.pem && \
    update-ca-certificates

# Copy local code to the container image.
COPY . ./

# Build jitaccess-runner.jar
RUN mvn package -DskipTests -Dquarkus.package.output-name=jitaccess -Dmaven.wagon.http.ssl.insecure=true

#
# Package stage.
#
FROM --platform=linux/amd64 openjdk:17
WORKDIR /app

#  Load CORP certs
COPY --from=build /etc/ssl/certs /etc/ssl/certs 

COPY --from=build /app/target/jitaccess-runner.jar .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/jitaccess-runner.jar", "-Dhttp.proxyHost=http://proxy.corp", "-Dhttp.proxyPort=8080", "-Dhttp.proxySet=true", "-Dhttps.proxyHost=http://proxy.corp", "-Dhttps.proxyPort=8080", "-Dhttps.proxySet=true"] 
