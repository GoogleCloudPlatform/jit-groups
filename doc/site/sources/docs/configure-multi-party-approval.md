
This article describes how you can configure the Just-in-Time Access application to support 
[multi-party approval](multi-party-approval.md).

Multi-party approval requires additional configuration and is disabled by default.

## Grant the Just-in-Time Access application permission to sign tokens

The Just-in-Time Access application uses activation tokens to pass information about approval requests between parties.
To protect against tampering, the application signs activation tokens using the Google-managed
service account key of its service account.

To let the application sign tokens using its service account, do the following:

1.  Set an environment variable to contain your project ID:

        gcloud config set project PROJECT_ID

    Replace `PROJECT_ID` with the ID of your project.

1.  Enable the IAM Credentials API:

        gcloud services enable iamcredentials.googleapis.com
    
1.  Grant the **Service Account Token Creator** role (`roles/iam.serviceAccountTokenCreator`)
    to the application's service account. This role lets the Just-In-Time Access application use 
    the service account to sign and verify activation tokens.
    
    === "App Engine"

            APPENGINE_VERSION=$(gcloud app versions list --service default --hide-no-traffic --format "value(version.id)")
            SERVICE_ACCOUNT=$(gcloud app versions describe $APPENGINE_VERSION --service default --format "value(serviceAccount)")

            gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
                --member "serviceAccount:$SERVICE_ACCOUNT" \
                --role "roles/iam.serviceAccountTokenCreator"

    === "Cloud Run"

            SERVICE_ACCOUNT=$(gcloud run services describe jitaccess --format "value(spec.template.spec.serviceAccountName)")

            gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
                --member "serviceAccount:$SERVICE_ACCOUNT" \
                --role "roles/iam.serviceAccountTokenCreator"

## Configure SMTP

The Just-In-Time Access application notifies users about multi-party approval requests by email. 
To send email, the application needs access to an SMTP mail server. You can use Google Workspace,
your corporate email server, or any other SMTP server for this purpose.

### Obtain SMTP credentials

=== "Google Workspace"

    You can let the Just-In-Time Access application send email through Google Workspace by using 
    [the Gmail SMTP server :octicons-link-external-16:](https://support.google.com/a/answer/176600?hl=en#gmail-smpt-option) and a 
    dedicated Google Workspace user account.

    To create a new user account in Google Workspace, do the following:

    1.  Open the [Google Workspace Admin Console :octicons-link-external-16:](https://admin.google.com/) and sign in as a super-admin user.
    1.  In the menu, go to **Directory > Users** and click **Add new user** to create a user.
    1.  Provide an appropriate name and email address such as the following:

        * **First Name**: a name such as `JIT Access`
        * **Last Name**: a name such as `Notifications`
        * **Primary email**: an email address such as `jitaccess-notifications`

    1.  Click **Manage user's password, organizational unit, and profile photo** and configure the following settings:

        * **Password**: Select **Create password** and set a password
        * **Ask for a password change at the next sign-in**: **Disabled**

    1.  Click **Add new user**.
    1.  Click **Done**.

    Now create an [app password :octicons-link-external-16:](https://support.google.com/accounts/answer/185833?hl=en) for the new user account:

    1.  Open an incognito browser window and go to [Google Accounts :octicons-link-external-16:](https://accounts.google.com/).
    1.  Sign in with the new user account that you created.
    1.  Go to **Security > Signing in to Google > 2-step verification** and follow the steps to
        [turn on 2-step verification :octicons-link-external-16:](https://support.google.com/accounts/answer/185839).
    1.  Go to **Security > Signing in to Google > App passwords**

        !!! note
            The **App passwords** link isn't shown if you haven't turned on 2-step verification yet.
        
    1.  On the **App passwords** page, use the following settings:
        1. **Select app**: Select **Mail**
        1. **Select device**: Select **Other** and enter a name such as `JIT Access`
    1.  Click **Generate**.

        Take note of the generated app password, because you need it later.


=== "Microsoft 365"

    You can let the Just-In-Time Access application send email through Microsoft 365 by
    [using an Office 365 mailbox and SMTP AUTH :octicons-link-external-16:](https://learn.microsoft.com/en-us/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365#option-1-authenticate-your-device-or-application-directly-with-a-microsoft-365-or-office-365-mailbox-and-send-mail-using-smtp-auth-client-submission).

    1.  Open the [Admin Center :octicons-link-external-16:](https://admin.microsoft.com/).
    1.  Go to **Users > Active users** and [add a new user :octicons-link-external-16:](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/add-users?view=o365-worldwide#add-users-one-at-a-time-in-the-dashboard-view).
        Provide an appropriate name and email address such as the following:

        * **First Name**: a name such as `JIT Access`
        * **Last Name**: a name such as `Notifications`
        * **Primary email**: an email address such as `jitaccess-notifications`

        Take note of the user's password, because you need it later.
        
    1.  [Enable SMTP AUTH :octicons-link-external-16:](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/authenticated-client-smtp-submission#enable-smtp-auth-for-specific-mailboxes)
        for the new user.


### Create a secret

You now create a secret in [Secrets Manager :octicons-link-external-16:](https://cloud.google.com/secret-manager/docs) to store the SMTP password:

1.  Enable the Secret Manager API:

        gcloud services enable secretmanager.googleapis.com

1.  Create a new secret:

        gcloud secrets create jitaccess-smtp --replication-policy="automatic"
    
1.  Create a secret version and save the SMTP password:

        echo PASSWORD | gcloud secrets versions add jitaccess-smtp --data-file=-
    
    Replace `PASSWORD` with the password that you obtained in the previous step.

1.  Grant the **Secret Accessor** role (`roles/secretmanager.secretAccessor`)
    to the application's service account. This role lets the Just-In-Time Access application read the secret:

        gcloud secrets add-iam-policy-binding jitaccess-smtp \
          --member="serviceAccount:$SERVICE_ACCOUNT" \
          --role="roles/secretmanager.secretAccessor"

1.  Look up the resource ID of the secret:

        gcloud secrets versions describe latest --secret jitaccess-smtp --format "value(name)"
    
    Note the output, you'll need in a later step.

### Redeploy the application

You now update the configuration and redeploy the Just-in-Time Access application:

1.  Clone the GitHub repository and switch to the `latest` branch:

        git clone https://github.com/GoogleCloudPlatform/jit-access.git
        cd jit-access/sources
        git checkout latest

1.  Download the configuration file that you used previously to deploy the application and save it to a file app.yaml:

    === "App Engine"

            APPENGINE_VERSION=$(gcloud app versions list --service default --hide-no-traffic --format "value(version.id)")
            APPENGINE_APPYAML_URL=$(gcloud app versions describe $APPENGINE_VERSION --service default --format "value(deployment.files.'app.yaml'.sourceUrl)")
        
            curl -H "Authorization: Bearer $(gcloud auth print-access-token)" $APPENGINE_APPYAML_URL -o app.yaml
    
    === "Cloud Run"

            gcloud run services describe jitaccess --format yaml > app.yaml

1.  Open the file `app.yaml` in an editor and add the following configuration options:

    === "Google Workspace"

            SMTP_SENDER_ADDRESS: email_address
            SMTP_USERNAME: email_address
            SMTP_SECRET: secret_path

        Replace the following:

        *   `email_address`: the email address of the Google Workspace user that you created previously, for example `jitaccess-notifications@example.org`
        *   `app_password`: the app password that you created previously
        *   `secret_path`: the resource ID of the Secret Manager secret, for example `projects/PROJECT/secrets/jitaccess-smtp/versions/1`

    === "Microsoft 365"

            SMTP_HOST: smtp.office365.com
            SMTP_SENDER_ADDRESS: email_address
            SMTP_USERNAME: email_address
            SMTP_SECRET: secret_path

        Replace the following:

        *   `server`: the server name to use for SMTP
        *   `email_address`: the email address of the Microsoft 365 user that you created previously, 
            for example `jitaccess-notifications@example.org`
        *   `secret_path`: the resource ID of the Secret Manager secret, for example `projects/PROJECT/secrets/jitaccess-smtp/versions/1`

    === "Other email provider"

            SMTP_HOST: server
            SMTP_SENDER_ADDRESS: email_address
            SMTP_USERNAME: email_address
            SMTP_SECRET: secret_path

        Replace the following:

        *   `server`: the server name to use for SMTP
        *   `email_address`: the email address of the Google Workspace user that you created previously, 
            for example `jitaccess-notifications@example.org`
        *   `secret_path`: the resource ID of the Secret Manager secret, for example `projects/PROJECT/secrets/jitaccess-smtp/versions/1`

        For additional configuration options, see [Configuration](configuration-options.md).
    

    !!! note
        Make sure that the lines use the same indentation as existing items in the `env_variables` section.

1. Deploy the application with the updated configuration:

    === "App Engine"

            sed -i 's/java11/java17/g' app.yaml
            gcloud app deploy --appyaml app.yaml

    === "Cloud Run"

            PROJECT_ID=$(gcloud config get-value core/project)

            docker build -t gcr.io/$PROJECT_ID/jitaccess:latest .
            docker push gcr.io/$PROJECT_ID/jitaccess:latest

            IMAGE=$(docker inspect --format='{{index .RepoDigests 0}}'  gcr.io/$PROJECT_ID/jitaccess)
            sed -i "s|image:.*|image: $IMAGE|g" app.yaml

            gcloud run services replace app.yaml
