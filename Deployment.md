# Deploying Just-In-Time Access

This guide describes how you can deploy the Just-In-Time Access application in
your environment.

## Before you begin

Before you deploy, you must decide which part of your resource hierarchy you want
to manage  just-in-time privileged access for:

*   A single project
*   A folder containing multiple projects
*   All projects of your organization

To complete the deployment, you need the following:

*   Super-admin access to your Cloud Identity or Workspace account.
*   _Security Admin_ (`roles/iam.securityAdmin`) access to the project, folder, or
    organization that you want to manage using Just-In-Time Access.
*   A second Cloud Identity or Workspace user that you can use for testing purposes.

You also need a project to deploy the Just-In-Time Access application in:

1. In the Google Cloud Console, on the project selector page, select or create a Google Cloud project.

    Note: If you don't plan to keep the resources that you create in this procedure, create a project 
    instead of selecting an existing project. After you finish these steps, you can delete the project, removing all resources associated with the project.

    [**Go to project selector**](https://console.cloud.google.com/projectselector2/home/dashboard)

2.  Make sure that billing is enabled for your Google Cloud project. Learn how to check if billing is enabled on a project.

    [**Enable the Cloud Asset Inventory, Resource Manager, and Identity-Aware Proxy APIs**](https://console.cloud.google.com/flows/enableapi?apiid=cloudasset.googleapis.com,cloudresourcemanager.googleapis.com,iap.googleapis.com)



## Preparing the Google Cloud project

To prepare your Google Cloud project, do the following:

1.  Switch to your project in the Cloud Console and open Cloud Shell.

    [Open Cloud Shell](https://console.cloud.google.com/?cloudshell=true)

2.  Set your default [project ID](https://cloud.google.com/resource-manager/docs/creating-managing-projects):

    <pre class="devsite-click-to-copy">
    gcloud config set project <var>PROJECT_ID</var>
    </pre>

    Replace <var>PROJECT_ID</var> with the ID of your project.


### Creating an App Engine application

Prepare App Engine for the deployment of the Just-In-Time Access application:

1.  Create an App Engine application:

    <pre class="devsite-click-to-copy">
    gcloud app create --region <var>LOCATION</var>
    </pre>

    where <code><var>LOCATION</var></code>  is a [supported App Engine location](https://cloud.google.com/about/locations#region).

2.  Create a service account for the application:

    <pre class="devsite-click-to-copy">
    SERVICE_ACCOUNT=$(gcloud iam service-accounts create jitaccess --display-name "Just-In-Time Access" --format "value(email)")
    </pre>

6.  Grant the _Cloud Debugger Agent_ (`roles/clouddebugger.agent`) role to the
    service account so that you can use Cloud Debugger if necessary:

    <pre class="devsite-click-to-copy">
    gcloud projects add-iam-policy-binding $(gcloud config get-value core/project) \
      --member "serviceAccount:$SERVICE_ACCOUNT" \
      --role "roles/clouddebugger.agent"
    </pre>

    Note: Granting the _Cloud Debugger Agent_ role is optional, but if you skip
    this step you'll see warning messages in the application's log.

### Granting access to manage IAM bindings

You now grant the _Security Admin_ role to the application's service account. This
role lets the Just-In-Time Access application create temporary IAM bindings when
granting just-in-time access.

Because the _Security Admin_ role is highly privileged, you must limit access to
the application's service account, and the project that contains it:

*   Limit the number of users that can access the project, and avoid granting
    any user the _Owner_ or _Editor_ role.
*   Limit the number of users that can impersonate the service account.
    In particular, this includes users with the _Service Account User,
    Service Account Token Creator_ role.

To grant the _Security Admin_ role to the service account do the following:

1.  Grant the _Security Admin_ (`roles/iam.securityAdmin role`) and _Cloud Asset Viewer_
    (`roles/cloudasset.viewer`) on the part of your resource hierarchy you want
    to manage just-in-time privileged access for:

    * **Project**:

        <pre class="devsite-click-to-copy">
        SCOPE_ID=<var>RESOURCE_PROJECT_ID</var>
        SCOPE_TYPE=projects

        gcloud projects add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/iam.securityAdmin" \
          --condition None
        gcloud projects add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/cloudasset.viewer" \
          --condition None
        </pre>

        where <code><var>RESOURCE_PROJECT_ID</var></code> is the ID of the
        project you manage access for.

        Note: This is a different project than the one you're deploying Just-In-Time Access to.

    * **Folder**:

        <pre class="devsite-click-to-copy">
        SCOPE_ID=<var>RESOURCE_FOLDER_ID</var>
        SCOPE_TYPE=folders

        gcloud resource-manager folders add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/iam.securityAdmin" \
          --condition None
        gcloud resource-manager folders add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/cloudasset.viewer" \
          --condition None
        </pre>

        where <code><var>RESOURCE_FOLDER_ID</var></code> is the ID of the
        folder that contains the projects you manage access for.

    *   **Organization**:

        <pre class="devsite-click-to-copy">
        SCOPE_ID=<var>ORGANIZATION_ID</var>
        SCOPE_TYPE=organizations

        gcloud organizations add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/iam.securityAdmin" \
          --condition None
        gcloud organizations add-iam-policy-binding $SCOPE_ID \
          --member "serviceAccount:$SERVICE_ACCOUNT" \
          --role "roles/cloudasset.viewer" \
          --condition None
        </pre>

        where <code><var>ORGANIZATION_ID</var></code> is the ID of your organization.
        You can determine this ID by running `gcloud organizations list`.


### Granting access to resolve group memberships

The Just-In-Time Access application lets you grant eligible access to a specific
user or to an entire group. To evaluate group memberships, the application must
be allowed to read group membership information from your Cloud Identity
or Workspace account.

To grant the application's service account access permission to read group
memberships, do the following:

1.  Open the [Admin Console](https://admin.google.com/) and sign in as a super-admin user..
2.  Go to **Account > Admin Roles**.
3.  Click **Groups Reader** > **Admins**.
4.  Click **Assign service accounts**.
5.  Enter the following email address:

    <pre class="devsite-click-to-copy">
    jitaccess@<var>PROJECT_ID</var>.iam.gserviceaccount.com
    </pre>

    where <code><var>PROJECT_ID</var></code> is the ID of your project.

6.  Click **Add**.
7.  Click **Assign role**.


## Deploying the application

You're now ready to deploy the Just-In-Time Access application.


### Deploy to App Engine

To deploy the Just-In-Time Access application to App Engine, do the following:

1.  In Cloud Shell, clone the [GitHub repository](https://github.com/GoogleCloudPlatform/iam-privilege-manager)
    and switch to the `latest` branch:

    <pre class="devsite-click-to-copy">
    git clone https://github.com/GoogleCloudPlatform/iam-privilege-manager.git && \
      cd iam-privilege-manager/sources && \
      git checkout latest
    </pre>

2.  Create a configuration file:

    <pre class="devsite-click-to-copy">
    cat &lt;&lt; EOF > app.yaml
    runtime: java11
    instance_class: F2
    service_account: $SERVICE_ACCOUNT
    env_variables:
      RESOURCE_SCOPE: $SCOPE_TYPE/$SCOPE_ID
      ELEVATION_DURATION: 5
      JUSTIFICATION_HINT: "Bug or case number"
      JUSTIFICATION_PATTERN: ".*"
    EOF
    </pre>

    Optionally, you can customize the following environment variables:

    <table>
      <tr>
       <th>Name</th>
       <th>Description</th>
      </tr>
      <tr>
       <td>RESOURCE_SCOPE</td>
       <td>Part of the Google Cloud resource hierarchy managed using this application:
         <ul>
           <li><code>organizations/<var>ID</var></code> (all projects)</li>
           <li><code>folders/<var>ID</var></code> (projects underneath a specific project)</li>
           <li><code>projects/<var>ID</var></code> (specific project)</li>
         </ul>
        The application's service account must be granted access to the respective node of the resource hierarchy.
       </td>
      </tr>
      <tr>
       <td>ELEVATION_DURATION</td>
       <td>Duration (in minutes) for which privileged access is granted. Default is <code>2</code>.
       </td>
      </tr>
      <tr>
       <td>JUSTIFICATION_HINT</td>
       <td>Hint displayed in UI indicating which justification to provide.
       </td>
      </tr>
      <tr>
       <td>JUSTIFICATION_PATTERN</td>
       <td>Regular expression that a justification has to match. Can be used to
           validate that justifications include a ticket number, or follow a certain
           convention.
       </td>
      </tr>
    </table>

3.  Deploy the application:

    <pre class="devsite-click-to-copy">
    gcloud app deploy --appyaml app.yaml
    </pre>

    Note: If you see an error message `NOT_FOUND: Unable to retrieve P4SA`, retry the command.

    Notice the target URL in the output, you'll need it later.


Repeat the steps above every time you want to change the configuration, or deploy
a new version of the application.

### Configuring Identity-Aware-Proxy

To configure IAP, you must configure an OAuth consent screen:

1.  In the Cloud Console, go to **APIs & Services > OAuth consent screen**.
2.  Select **Internal** and click **Create**.
3.  On the **OAuth consent screen** page, enter the following information:
     1. **App name**: `Just-In-Time Access`
     2. **User support email**: Enter an email address that users can contact for support.
     3. **Developer contact information**: Enter an email address that Google can contact if necessary.
4.  Click **Save and continue**.
5.  On the **Scopes** page click **Save and continue**.
6.  On the **Summary** page, click **Back to dashboard**.


Enable IAP for the Just-In-Time Access application:

1.  In the Cloud Console, go to **Security > Identity-Aware-Proxy**.
2.  Set **IAP** to **enabled**.

You now need to define which users are allowed to access the Just-In-Time Access
application. You can grant access to individual users, groups, or an entire domain:


1. In the Cloud Console, go to **IAM & Admin > IAM**.
2. Click **Add** and enter the following values:
    1. **New principal**: Select a user, group, or domain.
    2. **Role**: **IAP-secured web app user**.
3. Click **Save**.

It can take a few minutes for the role binding to take effect.

Note: The **IAP-secured web app user** role enables users to open the
Just-In-Time Access application, but doesn't provide them access to any additional resources yet. 


## Testing just-in-time access

You can now test the process of granting eligible access, and using
Just-In-Time Access to activate it.


### Grant eligible access

Grant a second Cloud Identity or Workspace user eligible access to a project:

1.  Return to the Cloud Console.
2.  Use the project picker to select a project that is part of the resource hierarchy
    managed by the Just-In-Time Access application.
3.  Click **Add**.
4.  Enter the email address of your second Cloud Identity or Workspace user and
    select a role such as **Project > Browser**.
5.  Click **Add condition**.
6.  Enter a title such as `Eligible for JIT access`.
7.  Select **Condition editor** and paste the following CEL expression:

    <pre class="devsite-click-to-copy">
    has({}.jitAccessConstraint)
    </pre>

8.  Click **Save**.
9.  Click **Save**.


### Activate access

Now switch users and request temporary access to a resource.

1.  Open an incognito browser window and navigate to the URL of the Just-In-Time Access
    application.
2.  Sign in as the user who you've granted eligible access.
3.  In the Just-In-Time Access application, select a role and resource that you
    want to activate access for.
4.  Enter a justification such as `testing` and click **Request access**.
5.  On the next page, notice that your access has been activated and that it
    expires in 5 minutes.


### Analyze logs

Switch back to your administrative user and review the log:

1.  In the Cloud Console, go to **Logging > Logs Explorer**.
2.  Enter the following query:

    <pre class="devsite-click-to-copy">
    labels.event="api.activateRole"
    </pre>

3.  Click **Run query**.

    Notice that a log record has been created for each role you activated. The
    log record includes a set of labels that you can use to create custom filters:

    <pre>
    {
      "textPayload": "Activated '<var>ROLE</var>' for '<var>EMAIL</var>' on '//cloudresourcemanager.googleapis.com/projects/<var>PROJECT_ID</var>', justified by '<var>JUSTIFICATION</var>'",
      "severity": "INFO",
      "labels": {
        "resource": "//cloudresourcemanager.googleapis.com/projects/<var>PROJECT_ID</var>",
        "event": "api.activateRole",
        "role": "<var>ROLE</var>",
        "clone_id": "00c6...",
        "user": "<var>EMAIL</var>",
        "justification": "<var>JUSTIFICATION</var>"
      },
      ...
    }

    </pre>

## What's next

*   Lean how you can use [context-aware access to secure access to Just-In-Time Access](https://support.google.com/a/answer/9275380)
*   Read more about [IAM conditions](https://cloud.google.com/iam/docs/conditions-overview)
*   Configure [a custom domain for the Just-In-Time Access application](https://cloud.google.com/run/docs/mapping-custom-domains)

--- 

_Just-In-Time Access is an open-source project and not an officially supported Google product._

_All files in this repository are under the
[Apache License, Version 2.0](LICENSE.txt) unless noted otherwise._
