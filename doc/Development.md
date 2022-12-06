# Development

JIT Access is a standalone Java application that's based on Quarkus. You can debug and run
the application locally by using the following command:

```
mvn quarkus:dev -Dsuspend=y -Ddebug=true -Djitaccess.impersonateServiceAccount=SERVICE_ACCOUNT -Djitaccess.debug=true
```

This command does the following:
* Start Quarkus in development mode
* Impersonate a service account, and use that service account to call Google Cloud APIs
* Disable IAP authentication

In the command line above, replace `SERVICE_ACCOUNT` with a service account that:
* Your ADC can impersonate (i.e., you have the _Service Account Token Creator_ role on that service account
* Can access the Policy Analyzer API

You can then access the application on `http://localhost:8080/?debug=1`.


## HTTPS introspection

To introspect HTTPS traffic using tools like Fiddler, use the following additional parameters:

```
-DproxyHost=127.0.0.1 -DproxyPort=8888 -DproxySet=true -Djavax.net.ssl.trustStore=JKS_PATH -Djavax.net.ssl.trustStorePassword=JKS_PASSWORD
```

Replace the following:

* `JKS_PATH: Path to the keystore that contains the CA certificate of the introspection tool
* `JKS_PASSWORD`: Password of the keystore (typically, `changeit`)
