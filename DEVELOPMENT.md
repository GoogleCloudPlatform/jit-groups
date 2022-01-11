# Development

IAM Elevate is based on [Quarkus](https://quarkus.io/) and designed to run in the
[App Engine Java 11 Standard Environment](https://cloud.google.com/appengine/docs/standard/java11).

## Prerequisites

To build and run the application locally, you need:

* OpenJDK 11 or later
* [Apache Maven](https://maven.apache.org/download.cgi)

Make sure both `java` and `mvn` are available in your `PATH`.

## Preparing a Google Cloud development project

Follow the instructions in [LINK TO DOC ONCE PUBLISHED](#) to prepare a Google Cloud  project.

## Run locally

To run the application locally, do the following:

```
cd sources
mvn quarkus:dev -Delevate.impersonateServiceAccount=SERVICE_ACCOUNT -Delevate.principal=TEST_PRINCIPAL
```

Replace the following:

* `SERVICE_ACCOUNT`: Email address of the service account that you created when preparing the project.
* `TEST_PRINCIPAL`: Email address of a user. Because you can't use Identity-Aware-Proxy locally, the application will
  simulate this user to be authenticated.

You can now access the application on [http://localhost:8080](http://localhost:8080).

## Running tests

To run the JUnit tests, you have to:

* Log in by running `gcloud auth application-default login`.
* Create a service account `no-access@` in your development project and grant yourself the `Token Account Creator`
  role on the service account so that you can impersonate it.
* Create a service account `temporary-access@` in your development project and grant yourself the `Token Account Creator`
  role on the service account so that you can impersonate it.
* Create a file `sourcs/test.properties` with the following content:
  ```
  test.project = PROJECT_ID 
  ```
  
  Where `PROJECT_ID` is the ID of your development project.

--- 

_IAM Elevate is an open-source project and not an officially supported Google product._

_All files in this repository are under the
[Apache License, Version 2.0](LICENSE.txt) unless noted otherwise._