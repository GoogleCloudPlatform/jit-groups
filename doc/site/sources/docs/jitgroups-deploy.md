# Deploy JIT Groups

This article describes how to deploy JIT Groups using Terraform.

## Before you begin

To complete the deployment, you need the following:

+   Super-admin access to the Cloud Identity or Google Workspace account that 
    corresponds to the Google Cloud organization that you're using.
+   A Google Cloud project to deploy the JIT Groups application in.

In the Google Cloud console, on the project selector page, select or create a Google Cloud project.

+   In the Google Cloud console, on the project selector page, select or create a Google Cloud project.

    [Go to project selector](https://console.cloud.google.com/projectselector2/home/dashboard){ .md-button }

+   [Make sure that billing is enabled for your Google Cloud project](https://cloud.google.com/billing/docs/how-to/verify-billing-enabled#console).

## Prepare the project

Prepare your project and configure Terraform to store its state in a Cloud Storage bucket:

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
    gcloud storage buckets create gs://$PROJECT_ID-state
    gcloud storage buckets update gs://$PROJECT_ID-state --versioning
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

## Deploy the application

Use Terraform to deploy JIT Groups to App Engine.

!!!note

    JIT Groups can be deployed to either App Engine or Cloud Run. Cloud Run requires a more complex
    configuration than deploying the application to App Engine. This article therefore
    focuses on App Engine.


1.  Clone the GitHub repository to the `target` directory and switch 
    to the `jitgroups/latest` branch:

    ```sh
    (mkdir -p target && \
     git clone https://github.com/GoogleCloudPlatform/jit-access.git && \
     cd jit-access && \
     git checkout jitgroups/latest)
    ```

1.  Create a Terraform configuration file and paste the following content:

    ```hcl
    module "application" {
        source                      = "target/jitaccess/terraform/jitgroups-appengine"
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
        
        # Optional, only needed for JIT Groups 1.x compatibility
        # resource_scope            = "RESOURCE_SCOPE"
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
    +   `iap_users` (optional): List of users or groups to allow access to the JIT Groups applications.

        *   Prefix users with `user:`, for example `user:bob@example.com`.
        *   Prefix groups with `group:`, for example `user:eng@example.com`.

    +   `environments` List of environment service accounts, leave empty for now.
    +   `options` (optional): Map of additional
        [configuration options](jitgroups-options.md).

    If you're upgrading from JIT Groups 1.x, uncomment the `resource_scope` line and replace `RESOURCE_SCOPE` 
    with one of the following:

    *   `projects/PROJECT-ID` where `PROJECT-ID` is the project that you want to manage
        just-in-time privileged access for.
    *   `folders/FOLDER-ID` where `FOLDER-ID` is the ID of the folder that you want to
        manage just-in-time privileged access for.
    *   `organizations/ORGANIZATION-ID` where `ORGANIZATION-ID` is the ID of the organization
        that you want to manage just-in-time privileged access for.


1.  Save the file using a `.tf` file extension.


1.  Authorize `terraform`:

    ```sh
    gcloud auth application-default login
    ```

1.  Initialize Terraform:

    ```sh
    gcloud auth application-default set-quota-project $PROJECT_ID
    terraform init 
    ```

1.  Apply the configuration:

    ```sh
    terraform apply 
    ```

    Because of internal provisioning delays, you might encounter the following
    error when you run `terraform apply` for the first time:
    `Error waiting for Creating StandardAppVersion: Error code 13, message: Failed to create cloud build`,
    If this happens, rerun `terraform apply`.

    When the command completes, it prints the URL of the application and the
    email address of the application's service account. You need this email address
    in the next step.

## Grant access to Cloud Identity/Workspace

To allow JIT Groups to manage Cloud Identity groups, you must grant it an additional
admin role in your Cloud Identity or Workspace account. Because this step requires super-admin
access to your Cloud Identity or Workspace, it's not performed automatically by Terraform.

You only need to perform these steps once.

1.  Open the Google Admin console and sign in as a super-admin user.
1.  Go to **Account > Admin Roles**:

    [Open Admin Roles](https://admin.google.com/ac/roles){ .md-button }

1.  Go to **Admin Roles**.
1.  Click **Groups Admin > Admins**.
1.  Click **Assign service accounts**.
1.  Enter the email address of the application's service account, then click **Add**.
1.  Click **Assign role**.

## Optional: Submit your configuration to Git

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
    END
    ```

1.  Commit your changes:

    ```sh
    git add -A && git commit -m 'Initial JIT Groups deployment'
    ```