# Prepare the project

1.  Create a project to deploy JIT Access in.

1.  Set an environment variable to contain your project ID:

    ```
    gcloud config set project PROJECT_ID
    ```
    
    Replace `PROJECT_ID` with the ID of the project to deploy JIT Access in.

1.  Enable the APIs for Cloud Storage:

    ```
    gcloud services enable storage-api.googleapis.com
    ```

1.  Create a Cloud Storage bucket to store the Terraform state and enable object versioning: 

    ```
    PROJECT_ID=$(gcloud config get core/project)
    gcloud storage buckets create gs://$PROJECT_ID-state
    gcloud storage buckets update gs://$PROJECT_ID-state --versioning
    ```

1.  Create a configuration file that instructs Terraform to store the state in the Cloud Storage bucket:

    ```
    cat << EOF > .project.tf
    terraform {
      backend "gcs" {
        bucket   = "$PROJECT_ID-state"
        prefix   = "terraform/{{ directory }}"
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

1.  Create a Terraform configuration file and paste the following content:

    ```
    module "jit-access-appengine" {
        source          = "MODULE_PATH/jit-access-appengine"
        project_id      = local.project_id
        
        customer_id     = "CUSTOMER_ID"
        admin_email     = "ADMIN_EMAIL"
        location        = "LOCATION"
        resource_scope  = "RESOURCE_SCOPE"
        iap_users       = []
        options         = {
            "JUSTIFICATION_HINT" = "Bug or case number"
        }
    }
    ```

    Replace the following:

    +   `MODULE_PATH`: the path to the `terraform` folder in the JIT Access repository.
    +   `CUSTOMER_ID`: your [Cloud Identity or Google Workspace account's customer ID](https://support.google.com/a/answer/10070793).
    +   `LOCATION`: a supported [App Engine location](https://cloud.google.com/about/locations#region).
    +   `RESOURCE_SCOPE`: one of the following:

        *   `projects/PROJECT-ID` where `PROJECT-ID` is the project that you want to manage 
            just-in-time privileged access for.
        *   `folders/FOLDER-ID` where `FOLDER-ID` is the ID of the folder that you want to 
            manage just-in-time privileged access for.
        *   `organizations/ORGANIZATION-ID` where `ORGANIZATION-ID` is the ID of the organization 
            that you want to manage just-in-time privileged access for.
            
    +   `iap_users` (optional): List of users or groups to allow access to the JIT Access applications.

        *   Prefix users with `user:`, for example `user:bob@example.com`.
        *   Prefix groups with `group:`, for example `user:eng@example.com`.
        
    +   `options` (optional): Map of additional 
        [configuration options](https://googlecloudplatform.github.io/jit-access/configuration-options/).

1.  Save the file using a `.tf` file extension.

# Deploy

1.  Authenticate terraform:

    ```
    gcloud auth application-default login
    ```
    
1.  Initialize Terraform:

    ```
    terraform init 
    ```

1.  Apply the configuration:

    ```
    terraform apply 
    ```
    
    When the command completes, it prints the URL of the application and the 
    email address of the application's service account. You need this email address
    in the next step.

# Grant access to allow the application to resolve group memberships

To allow JIT Access to resolve group membership, you must grant it an additional
admin role in your Cloud Identity or Workspace account:

1.  Open the Google Admin console and sign in as a super-admin user.

1.  Go to **Account > Admin Roles**:
1.  Go to **Admin Roles**.
1.  Click **Groups Reader > Admins**.
1.  Click **Assign service accounts**.

    Enter the email address of the application's service account.

1.  Click **Add**.
1.  Click **Assign role**.