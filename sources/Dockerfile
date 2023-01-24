FROM maven:3.8.7-eclipse-temurin-17-alpine AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml package -DskipTests

#
# Package stage
#
FROM openjdk:17
COPY /target/jitaccess-1.2.0-runner.jar /usr/local/lib/jitaccess-1.2.0-runner.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/jitaccess-1.2.0-runner.jar"]
