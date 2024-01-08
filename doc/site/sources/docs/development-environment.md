
JIT Access is a standalone Java application that's based on [Quarkus](https://quarkus.io/) and designed to run in the
[App Engine Java 17 Standard Environment :octicons-link-external-16:](https://cloud.google.com/appengine/docs/standard/java-gen2/runtime)
or on Cloud Run.

## Prerequisites

To build and run the application locally, you need:

* JDK 17 or later
* [Apache Maven](https://maven.apache.org/download.cgi)

Make sure both `java` and `mvn` are available in your `PATH`.

You also need a Google Cloud development project. Follow the instructions in
[Manage just-in-time privileged access to projects :octicons-link-external-16:](https://cloud.google.com/architecture/manage-just-in-time-privileged-access-to-project) to prepare a project.


## Run locally

You can debug and run the application locally by using the following command:

    mvn quarkus:dev -Dsuspend=y -Ddebug=true -Djitaccess.impersonateServiceAccount=SERVICE_ACCOUNT -Djitaccess.debug=true

This command does the following:
* Start Quarkus in development mode
* Impersonate a service account, and use that service account to call Google Cloud APIs
* Disable IAP authentication

In the command line above, replace `SERVICE_ACCOUNT` with a service account that:
* Your ADC can impersonate (i.e., you have the _Service Account Token Creator_ role on that service account
* Can access the Policy Analyzer API

You can then access the application on `http://localhost:8080/?debug=1`. Use the debug panel in the lower left
to specify a test principal. The debug panel also lets you mock backend calls, which can be useful for frontend
development.


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