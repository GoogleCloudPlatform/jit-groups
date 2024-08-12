
JIT Groups is a standalone Java application that's based on [Quarkus](https://quarkus.io/) and designed to run in the
[App Engine Java 17 Standard Environment :octicons-link-external-16:](https://cloud.google.com/appengine/docs/standard/java-gen2/runtime)
or on Cloud Run.

## Prerequisites

To build and run the application locally, you need:

* JDK 17 or later
* [Apache Maven](https://maven.apache.org/download.cgi)

Make sure both `java` and `mvn` are available in your `PATH`.

## Prepare a development project

To run JIT Groups locally, you need a development project. The quickest way to set up
a project is to do the following:

1.  Follow the instructions in [Deploy JIT Groups](jitgroups-deploy.md) to deploy JIT Groups to a development project.
1.  Grant your own user account the _Service Account Token Creator_ on the service account used by JIT Groups.


## Run locally

You can debug and run the application locally by using the following command:

    mvn quarkus:dev -Dsuspend=y -Ddebug=true -Djitaccess.impersonateServiceAccount=SERVICE_ACCOUNT -Djitaccess.debug=true

Replace `SERVICE_ACCOUNT` with the email address of the service account used by JIT Groups.

This command does the following:

+   Start Quarkus in development mode.
+   Impersonate the JIT Groups service account.
+   Disable IAP authentication.

You can then access the application on `http://localhost:8080/`. Use the debug panel in the lower left
to specify a test principal. 


### HTTPS introspection

To introspect HTTPS traffic using tools like Fiddler, use the following additional parameters:

    -DproxyHost=127.0.0.1 -DproxyPort=8888 -DproxySet=true -Djavax.net.ssl.trustStore=JKS_PATH -Djavax.net.ssl.trustStorePassword=JKS_PASSWORD

Replace the following:

*   `JKS_PATH`: Path to the keystore that contains the CA certificate of the introspection tool
*   `JKS_PASSWORD`: Password of the keystore (typically, `changeit`)


## Run tests

To run the JUnit tests, do the following:

*   Create a service account `no-access@` in your development project and grant yourself the `Token Account Creator`
    role on the service account so that you can impersonate it.
*   Create a service account `temporary-access@` in your development project and grant yourself the `Token Account Creator`
    role on the service account so that you can impersonate it.
*   Create a file `sources/test.properties` with the following content:
  
        test.project = PROJECT_ID 

    Where `PROJECT_ID` is the ID of your development project.