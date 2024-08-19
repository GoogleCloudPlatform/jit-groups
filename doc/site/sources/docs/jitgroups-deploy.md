# Deploy JIT Groups

This article describes how to deploy JIT Groups in your Google Cloud organization. The deployment uses Terraform
and creates the following resources:

+   An App Engine application that runs JIT Groups and can be accessed through Identity-Aware Proxy.
+   An OAuth consent screen and client ID for Identity-Aware Proxy.
+   A service account that JIT Groups uses to access Google Cloud and Cloud Identity APIs. The service account
    is attached to the App Engine application.
+   A Secret Manager secret to store credentials for your SMTP server.
+   A Cloud Storage bucket to store Terraform state. 

## Before you begin

To complete the deployment, you need the following:

+   A Google Cloud project to deploy the JIT Groups application in. 
+   Super-admin access to your Cloud Identity or Google Workspace account.

You also need one of the following premium subscriptions:

+ [Cloud Identity Premium :octicons-link-external-16:](https://cloud.google.com/identity)
+ [Google Workspace Enterprise :octicons-link-external-16:](https://support.google.com/a/answer/6043385?co=DASHER._Family%3DEnterprise&oco=0) 
  Standard, Plus, or Education 

!!!tip
    **It's sufficient to purchase licenses for a subset of your users**, or even a single user. 

    JIT Groups requires a premium subscription, but _doesn't_ require a premium license for all users. The presence
    of a subscription is sufficient to enable all users to use JIT Groups, including those users that only have a 
    Cloud Identity Free license.

JIT Groups requires one of these premium subscription because it uses 
[group membership expirations](https://cloud.google.com/identity/docs/how-to/manage-expirations), which
is a premium feature. Without a premium subscription, you can deploy JIT Groups, but attempting to join 
a group will fail.

If you don't have a Cloud Identity Premium or Google Workspace Enterprise subscription, you can
trial Cloud Identity Premium for free:

[Trial Cloud Identity Premium](https://admin.google.com/ac/billing/buy?action=BUY&sku_id=GOOGLE.IDENTITY_PRO_SKU&journey=83){ .md-button }


## Prepare the project

Create a Cloud Storage bucket and configure Terraform to use this Cloud Storage bucket for storing its state:

1.  Open Cloud Shell or a local terminal.

    [Open Cloud Shell](https://console.cloud.google.com/?cloudshell=true){ .md-button }

1.  Authorize `gcloud`:

    ```sh
    gcloud auth login
    ```

1.  Set an environment variable to contain [your project ID](https://cloud.google.com/resource-manager/docs/creating-managing-projects):

    ```sh
    gcloud config set project PROJECT_ID
    ```

    Replace `PROJECT_ID` with the ID of the project to deploy JIT Groups in.

1.  Create a Cloud Storage bucket to store the Terraform state and enable object versioning:

    ```sh
    PROJECT_ID=$(gcloud config get core/project)
    gcloud services enable storage-api.googleapis.com
    gcloud storage buckets create gs://$PROJECT_ID-state --uniform-bucket-level-access
    gcloud storage buckets update gs://$PROJECT_ID-state --versioning
    ```

1.  Create an empty directory and enter the directory:

    ```sh
    mkdir -p deployment && cd deployment
    ```
    
1.  Create a configuration file that instructs Terraform to store its state in the Cloud Storage bucket:

    ```hcl
    cat << EOF > _project.tf
    terraform {
      backend "gcs" {
        bucket   = "$PROJECT_ID-state"
        prefix   = "terraform"
      }
    }

    locals {
      project_id = "$PROJECT_ID"
    }

    provider "google" {
      project    = local.project_id
    }

    EOF
    ```

1.  Authorize `terraform`:

    ```sh
    gcloud auth application-default login &&
      gcloud auth application-default set-quota-project $PROJECT_ID
    ```


## Deploy the application

Use Terraform to deploy JIT Groups to App Engine.

!!!note

    JIT Groups can be deployed to either App Engine or Cloud Run. Cloud Run requires a more complex
    configuration than deploying the application to App Engine. This article therefore
    focuses on App Engine.


1.  Clone the GitHub repository to the `target` directory and switch 
    to the `jitgroups/latest` branch:

    ```sh
    git clone https://github.com/GoogleCloudPlatform/jit-access.git --branch jitgroups/latest target
    ```

1.  Create a Terraform configuration file named `main.tf` and paste the following content:

    ```hcl
    module "application" {
        source                      = "./target/terraform/jitgroups-appengine"
        project_id                  = local.project_id
        customer_id                 = "CUSTOMER_ID"
        groups_domain               = "DOMAIN"
        admin_email                 = "ADMIN_EMAIL"
        location                    = "LOCATION"
        iap_users                   = []
        environments                = []
        options                     = {
            # "APPROVAL_TIMEOUT"    = "90"
        }
    }

    output "url" {
        value                       = module.application.url
    }

    output "service_account" {
        value                       = module.application.service_account
    }
    ```

    Replace the following:

    +   `CUSTOMER_ID`: your [Cloud Identity or Google Workspace account's customer ID](https://support.google.com/a/answer/10070793).
    +   `DOMAIN`: the domain to use for Cloud Identity groups, this can be the primary or a secondary domain of
        your Cloud Identity or Google Workspace account.
    +   `ADMIN_EMAIL`: the email address to show as contact on the OAuth consent screen,
        this must be the email address of a Cloud Identity/Workspace user.
    +   `LOCATION`: a supported [App Engine location](https://cloud.google.com/about/locations#region).
    +   `iap_users` (optional): List of users or groups to allow access to the JIT Groups application.

        *   Prefix users with `user:`, for example `user:bob@example.com`.
        *   Prefix groups with `group:`, for example `user:eng@example.com`.
        *   To allow all users of your Cloud Identity or Workspace account, use `domain:PRIMARY_DOMAIN` where
            `PRIMARY_DOMAIN` is the primary domain of your Cloud Identity or Google Workspace account.

    +   `environments` List of environment service accounts, leave empty for now.
    +   `options` (optional): Map of additional
        [configuration options](jitgroups-options.md).

1.  Initialize Terraform:

    ```sh
    terraform init 
    ```

1.  Apply the configuration:

    ```sh
    terraform apply 
    ```

    !!!note
        Because of internal provisioning delays, you might encounter the following
        error when you run `terraform apply` for the first time:

        ```
        Error waiting for Creating StandardAppVersion: Error code 13, message:
        Failed to create cloud build
        ```

        If you encounter this error, rerun `terraform apply`.

    When the command completes, it prints the URL of the application and the
    email address of the application's service account. You need this URL and email address
    later.

### Grant access to Cloud Identity/Workspace

To allow JIT Groups to manage Cloud Identity security groups, you must grant it the 
[Groups Admin role :octicons-link-external-16:](https://support.google.com/a/answer/2405986?hl=en#:~:text=another%20admin.-,Groups%20Admin,-Has%20full%20control)
in your Cloud Identity or Workspace account. Because this step requires super-admin
access to your Cloud Identity or Workspace account, it's not performed automatically by Terraform.

You only need to perform these steps once.

1.  Open the Google Admin console and sign in as a super-admin user.
1.  Go to **Account > Admin Roles**:

    [Open Admin Roles](https://admin.google.com/ac/roles){ .md-button }

1.  Click **Groups Admin > Admins**.
1.  Click **Assign service accounts**.
1.  Enter the email address of the application's service account that you obtained after running `terraform apply`. 
    Then click **Add**.
1.  Click **Assign role**.

### Access the JIT Groups web interface

You can now access the JIT Groups web interface:

1.  Open a browser and navigate to the URL that you obtained after running `terraform apply`.
1.  Authenticate with a user account that's allowed to access the JIT Groups application. 
    These user accounts include:

    +   The user configured as `admin_email` in the Terraform configuration.
    +   All users or groups configured in `iap_users` in the Terraform configuration.

Because you haven't configured an environment yet, JIT Groups uses an example environment
named `example`. This environment demonstrates some of the features provided by JIT Groups,
but doesn't let you request access to any groups or resources.

To configure an environment, see [Add an environment](jitgroups-environment.md).

### Optional: Configure email notifications

You can configure a JIT group so that joining the group requires approval from another user. To
notify users about pending approvals, JIT Groups must be able to send emails.

To let JIT Groups send emails, you must grant it access to an SMTP mail server. You can use Google Workspace, 
Microsoft 365, or any other SMTP server for this purpose.

To configure email notifications, do the following:

1.  Obtain credentials for your SMTP server:
    
    === "Google Workspace"
    
        You can let JIT Groups send email through Google Workspace by using 
        [the Gmail SMTP server :octicons-link-external-16:](https://support.google.com/a/answer/176600?hl=en#gmail-smpt-option) and a 
        dedicated Google Workspace user account.
    
        To create a new user account in Google Workspace, do the following:
    
        1.  Open the [Google Workspace Admin Console :octicons-link-external-16:](https://admin.google.com/) and sign in as a super-admin user.
        1.  In the menu, go to **Directory > Users** and click **Add new user** to create a user.
        1.  Provide an appropriate name and email address such as the following:
    
            * **First Name**: a name such as `JIT Groups`
            * **Last Name**: a name such as `Notifications`
            * **Primary email**: an email address such as `jitgroups-notifications`
    
        1.  Click **Manage user's password, organizational unit, and profile photo** and configure the following settings:
    
            * **Password**: Select **Create password** and set a password
            * **Ask for a password change at the next sign-in**: **Disabled**
    
        1.  Click **Add new user**.
        1.  Click **Done**.
        
        Assign a Google Workspace license to the new user account:
        
        1.  Refresh the list of users.
        1.  Open the details for the user account that you just created.
        1.  Click **Licenses**.
        1.  Set the status for **Google Workspace** to **assigned**.
        
            !!! important
            
                You must assign a Google Workspace license. Without a Google Workspace license, 
                the Gmail SMTP server rejects email delivery.
                
        1.  Click **Save**.
        
    
        Create an [app password :octicons-link-external-16:](https://support.google.com/accounts/answer/185833?hl=en) for the new user account:
    
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
    
        You can let JIT Groups send email through Microsoft 365 by
        [using an Office 365 mailbox and SMTP AUTH :octicons-link-external-16:](https://learn.microsoft.com/en-us/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365#option-1-authenticate-your-device-or-application-directly-with-a-microsoft-365-or-office-365-mailbox-and-send-mail-using-smtp-auth-client-submission).
    
        1.  Open the [Admin Center :octicons-link-external-16:](https://admin.microsoft.com/).
        1.  Go to **Users > Active users** and [add a new user :octicons-link-external-16:](https://learn.microsoft.com/en-us/microsoft-365/admin/add-users/add-users?view=o365-worldwide#add-users-one-at-a-time-in-the-dashboard-view).
            Provide an appropriate name and email address such as the following:
    
            * **First Name**: a name such as `JIT Groups`
            * **Last Name**: a name such as `Notifications`
            * **Primary email**: an email address such as `jitgroups-notifications`
    
            Take note of the user's password, because you need it later.
            
        1.  [Enable SMTP AUTH :octicons-link-external-16:](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/authenticated-client-smtp-submission#enable-smtp-auth-for-specific-mailboxes)
            for the new user.

1.  Save the SMTP password in Secret Manager:

    ```sh
    echo "Enter SMTP password:" && (read -s PASSWORD && \
      echo "Saving..." &&
      echo $PASSWORD | gcloud secrets versions add smtp --data-file=-)
    ```

1.  Open your [existing Terraform configuration](jitgroups-deploy.md) and the following two arguments: 

    ```hcl  hl_lines="14-15"
    module "application" {
        source                      = "./target/terraform/jitgroups-appengine"
        project_id                  = local.project_id
        customer_id                 = "CUSTOMER_ID"
        groups_domain               = "DOMAIN"
        admin_email                 = "ADMIN_EMAIL"
        location                    = "LOCATION"
        iap_users                   = []
        environments                = []
        options                     = {
            # "APPROVAL_TIMEOUT"    = "90"
        }
        
        smtp_host                   = "SMTP_HOST"
        smtp_user                   = "SMTP_USER"
    }

    output "url" {
        value                       = module.application.url
    }

    output "service_account" {
        value                       = module.application.service_account
    }
    ```

    Replace the following:

    === "Google Workspace"

        *   `SMTP_HOST`: `smtp.gmail.com`
        *   `SMTP_USER`: the email address of the Google Workspace user that you created previously, for example `jitgroups-notifications@example.org`

    === "Microsoft 365"

        *   `SMTP_HOST`: `smtp.office365.com`
        *   `SMTP_USER`: the email address of the Microsoft 365 user that you created previously, 
            for example `jitgroups-notifications@example.org`

    === "Other email provider"

        *   `SMTP_HOST`: DNS name of the SMTP server
        *   `SMTP_USER`: user name for authentication


1.  Apply the configuration change:

    ```sh
    terraform apply 
    ```

### Optional: Submit your configuration to Git

To simplify future upgrades and configuration changes, submit your configuration to Git:

1.  Initialize a `git` repository:

    ```sh
    git init
    ```
    
1.  Create a `.gitignore` that excludes the JIT Groups source code and local Terraform files 
    from being committed to the repository:
 
    ```sh
    cat << EOF > .gitignore
    # JIT Groups source code
    target/
 
    # Local .terraform directories
    **/.terraform/*
    .terraform.*
    EOF
    ```

1.  Commit your changes:

    ```sh
    git add -A && git commit -m 'Initial JIT Groups deployment'
    ```
    
## What's next

[Add an environment :material-arrow-right:](jitgroups-environment.md){ .md-button }