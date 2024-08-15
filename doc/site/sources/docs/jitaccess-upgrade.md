If you're currently using JIT Access, you can upgrade to JIT Groups. This article describes
the functional differences between JIT Groups and JIT Access, and how to perform an upgrade.

## Functional changes

JIT Access has been narrowly focussed on _privileged access management_ and the ability to activate
individual IAM role bindings for short period of time (typically less than a day).

JIT Groups expands this scope to include _self-service access management_, or _entitlement management_. 
You can use JIT Groups not only for managing privileged access, but all types of access to Google Cloud 
resources.

The following table summarizes key differences between JIT Groups and JIT Access:

|                                            | JIT Groups                                                                                     | JIT Access                   |
|--------------------------------------------|------------------------------------------------------------------------------------------------|------------------------------|
| Use cases                                  | - Entitlement management<br>- Self-service access management<br>- Privileged access management | Privileged access management |
| Entitlements being managed                 | Memberships to Cloud Identity security groups                                                  | IAM role bindings            |
| Scope of configuration                     | JIT group, system, environment                                                                 | Global only                  |
| Granularity of entitlements                | Groups can "bundle" any number of roles, across one or more projects                           | Single IAM role              |
| Grant access to projects                   | :material-check:                                                                               | :material-check:             |
| Grant access to resources (IAM conditions) | :material-check:                                                                               | :material-check:             |
| Activation without approval                | :material-check:                                                                               | :material-check:             |
| Activation with peer approval              | :material-check:                                                                               | :material-check:             |
| Activation with manager approval           | :material-check:                                                                               | :x:                          |


## Compatibility with JIT Access

JIT Groups supports "JIT Access-style" eligible role bindings. When you enable JIT Access compatibility, JIT Groups
surfaces such eligible role bindings as a JIT Group:

+    Role bindings that use the IAM condition `has({}.jitAccessConstraint)` are mapped to JIT Groups that permit
     self-approval.
+    Role bindings that use the IAM condition `has({}.multiPartyApprovalConstraint)` are mapped to JIT Groups that require
     peer approval.

This compatibility is subject to the following limitations:

+    The web interface lists all available projects instead of a personalized list of projects. This behavior is similar
     to the [`AssetInventory`](configure-catalogs.md) catalog.
+    A small number of predefined IAM roles can't be mapped because their name exceeds the limits imposed by Cloud Identity for group names. 
+    Roles that use [resource conditions](resource-conditions.md) can't be mapped.
+    When requesting approval to join a group, users can't select among possible approvers. Instead, the approval
     request is sent to all users who are allowed to approve.
+    JIT Groups doesn't support [Pub/Sub notifications](pubsub-notifications.md).


## Upgrade an existing deployment

To upgrade your existing JIT Access deployment, do the following:

1.  Open Cloud Shell or a local terminal.

    [Open Cloud Shell](https://console.cloud.google.com/?cloudshell=true){ .md-button }

1.  Set an environment variable to contain [your project ID](https://cloud.google.com/resource-manager/docs/creating-managing-projects):

    ```sh
    gcloud config set project PROJECT_ID
    ```

    Replace `PROJECT_ID` with the ID of the project that contains your JIT Access deployment.

1.  Clone the
    [GitHub repository](https://github.com/GoogleCloudPlatform/iam-privilege-manager)
    and switch to the `jitgroups/latest` branch:

    ```sh
    git clone https://github.com/GoogleCloudPlatform/jit-access.git
    cd jit-access/sources
    git checkout jitgroups/latest
    ```

1.  Download the configuration file that you used previously to deploy the
    application and save it to a file `app.yaml`:

    === "App Engine"

        ```
        APPENGINE_VERSION=$(gcloud app versions list --service default --hide-no-traffic --format "value(version.id)")
        APPENGINE_APPYAML_URL=$(gcloud app versions describe $APPENGINE_VERSION --service default --format "value(deployment.files.'app.yaml'.sourceUrl)")
    
        curl -H "Authorization: Bearer $(gcloud auth print-access-token)" $APPENGINE_APPYAML_URL -o app.yaml
        cat app.yaml
        ```
   
        If downloading the file `app.yaml` fails, you can download your current
        configuration [in the Cloud Console](https://console.cloud.google.com/appengine/versions?serviceId=default).

    === "Cloud Run"

        ```sh
        gcloud config set run/region REGION
        gcloud run services describe jitaccess --format yaml > app.yaml
        ```
   
        Replace `REGION` with the region that contains your
        existing Cloud Run deployment.

1.  Verify that you're using the `AssetInventory` catalog:

    ```sh
    grep RESOURCE_CATALOG app.yaml
    ```
    
    If you see the output `RESOURCE_CATALOG: AssetInventory`, then you're using the `AssetInventory` catalog. Otherwise,
    [you must switch to the  `AssetInventory` catalog](http://localhost:8000/jit-access/configure-catalogs/#assetinventory-catalog)
    first before you can proceed with the upgrade.

1.  Add an environment variable to `app.yaml`:

    ```yaml
    GROUPS_DOMAIN: DOMAIN
    ```
    Replace `DOMAIN` with the domain to use for Cloud Identity groups, this can be the primary or a secondary domain of
    your Cloud Identity or Google Workspace account.

1.  Enable the Cloud Identity and Groups Settings APIs:

    ```sh
    gcloud services enable cloudidentity.googleapis.com groupssettings.googleapis.com
    ```

1.  Deploy the application:

    === "App Engine"

        ```sh
        sed -i 's/java11/java17/g' app.yaml
        gcloud app deploy --appyaml app.yaml
        ```

    === "Cloud Run"

        ```sh
        PROJECT_ID=$(gcloud config get-value core/project)

        docker build -t gcr.io/$PROJECT_ID/jitaccess:latest .
        docker push gcr.io/$PROJECT_ID/jitaccess:latest

        IMAGE=$(docker inspect --format='&#123;{index .RepoDigests 0}}'  gcr.io/$PROJECT_ID/jitaccess)
        sed -i "s|image:.*|image: $IMAGE|g" app.yaml

        gcloud run services replace app.yaml
        ```

To allow JIT Groups to manage Cloud Identity security groups, you must grant it the
[Groups Admin role :octicons-link-external-16:](https://support.google.com/a/answer/2405986?hl=en#:~:text=another%20admin.-,Groups%20Admin,-Has%20full%20control)
in your Cloud Identity or Workspace account. 

!!!note

    For JIT Access, it was sufficient to grant the _Groups Reader_ role because it only needed to read
    access to groups. JIT Groups requires the _Groups Admin_ role so that it can create and manage security groups.

You only need to perform these steps once.

1.  Open the Google Admin console and sign in as a super-admin user.
1.  Go to **Account > Admin Roles**:

    [Open Admin Roles](https://admin.google.com/ac/roles){ .md-button }

1.  Click **Groups Admin > Admins**.
1.  Click **Assign service accounts**.
1.  Enter the email address of the application's service account, then click **Add**.
1.  Click **Assign role**.

### Test

You can now access the JIT Groups web interface:

1.  Select the `classic` environment. This environment contains the JIT croups that correspond to your
    eligible role bindings.
2.  Click **Reconcile** to let JIT Groups
    [check for legacy roles that can't be mapped to JIT groups](jitgroups-concepts.md#reconciliation).
    This process can take a few minutes.

3.  Verify the results to see if there are any groups that can't be mapped.

### Roll back

If JIT Groups doesn't work as expected, you can roll back the upgrade by doing the following:

=== "App Engine"

    1.  Open the Cloud Console and go to **App Engine > Versions**: 

        [Open App Engine Versions](https://console.cloud.google.com/appengine/versions?serviceId=default){ .md-button }

    1.  Select a previous version and click **Migrate traffic**.
    1.  In the Google Admin console, revoke the **Groups Admin** role for the application's service account.

=== "Cloud Run"

    1.  Open the Cloud Console and go to **Cloud Run**: 

        [Open Cloud Run](https://console.cloud.google.com/run){ .md-button }

    1.  Open the details for `jitaccess`.
    1.  Select the **Revisions** tab.
    1.  Select a previous version and click **... > Manage traffic**.
    1.  Assign 100% of traffic to the previous version and click **OK**.
    1.  In the Google Admin console, revoke the **Groups Admin** role for the application's service account.
    