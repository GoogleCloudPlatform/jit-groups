This article describes how you can configure [Cloud Build :octicons-link-external-16:](https://cloud.google.com/build/docs/overview)
so that it automatically deploys changes in your Terraform configuration.

## Before you begin

Before you can configure the automatic deployment, you first need to connect Cloud Build to your Git repository and
enable required APIs:

1.  Connect Cloud Build to your Git repository. 

    === "GitHub"

        To connect Cloud Build to a GitHub repository, do the following:

        1.  [Create a host connection :octicons-link-external-16:](https://cloud.google.com/build/docs/automating-builds/github/connect-repo-github#connecting_a_github_host)
            to connect Cloud Build to GitHub.
        1.  [Link your GitHub repository :octicons-link-external-16:](https://cloud.google.com/build/docs/automating-builds/github/connect-repo-github#connecting_a_github_repository_2)
            to let Cloud Build access your repository contents.

1.  Enable the App Engine Admin API:

    ```sh
    gcloud services enable appengine.googleapis.com
    ```

## Deploy automatically



1.  Create a service account for Cloud Build:

    ```sh
    DEPLOY_ACCOUNT=$(gcloud iam service-accounts create jitgroups-cloudbuild-deploy \
      --display-name "Service account for deployments " \
      --format "value(email)")
    ```

1.  Grant the service account the necessary roles to perform deployments:

    ```sh
    echo -n \
      roles/viewer \
      roles/logging.logWriter \
      roles/storage.objectAdmin \
      roles/appengine.appAdmin \
      roles/cloudbuild.builds.editor \
      roles/iam.serviceAccountUser \
      roles/oauthconfig.editor \
    | xargs -n 1 gcloud projects add-iam-policy-binding $PROJECT_ID \
      --member "serviceAccount:$DEPLOY_ACCOUNT" \
      --condition None \
      --format "value(etag)" \
      --role 
    ```

!!!important

    TODO: highly privileged, therefore maual approval.    

1.  In the Cloud Console, go to **Cloud Build > Triggers**:

    [Open Triggers](https://console.cloud.google.com/cloud-build/triggers){ .md-button }

1.  Click **Create trigger** and configure the following settings:

    +   **Name**: `deploy`
    +   **Region**: Select the region in which you created the connection.
    +   **Event**: **Push to a branch**
    +   **Source**: **2nd-gen**
    +   **Repository**: Select the Git repository that contains your
        Terraform configuration.
  
        If the right repository isn't listed, verify that you selected the right region.

    +   **Branch**: `^master$` or `^main$`, depending on the name of your main branch. 
    +   **Require approval before build executes**: **enabled**
    +   **Send logs to GitHub**: **enabled**
    +   **Service account**: `jitgroups-cloudbuild-deploy`

1.  Click **Create**.
    
1.  In your Git repository, create a build configuration file:

    ```sh
    touch cloudbuild.yaml
    ```
        
1.  Open the file `cloudbuild.yaml` and paste the following workflow configuration:
    
    ```yaml
    substitutions:
      _JITGROUPS_REF: 'TAG'
    steps:
    
    # Clone JIT Groups repository
    - name: 'alpine/git'
      args:
        - clone
        - https://github.com/GoogleCloudPlatform/jit-access.git
        - --branch
        - $_JITGROUPS_REF
        - target
    
    # Terraform: Init
    - name: 'hashicorp/terraform:1.9'
      args:
        - 'init'
        - '-no-color'
    
    # Terraform: Apply
    - name: 'hashicorp/terraform:1.9'
      args:
        - apply
        - -no-color
        - -input=false
        - -auto-approve
      timeout: '600s'
    
    options:
      logging: CLOUD_LOGGING_ONLY
    ```
        
    Replace `JITGROUPS_REF` with the JIT Groups tag or branch that you want to deploy, for example `tags/2.0.0` or
    `jitgroups/latest`.
    
   1.  Commit your changes and push them to the remote:
    
       ```sh
       git add -A && git commit -m 'Add Cloud Build configuration for deploying automatically'
       ```

TODO: Screenshot for approval

## Verify pull requests

1.  Create a service account for Cloud Build:

    ```sh
    VERIFY_ACCOUNT=$(gcloud iam service-accounts create jitgroups-cloudbuild-verify \
      --display-name "Service account for verification " \
      --format "value(email)")
    ```

1.  Grant the service account the necessary roles to verify deployments:

    ```sh
    echo -n \
      roles/viewer \
      roles/logging.logWriter \
      roles/storage.objectUser \
      roles/oauthconfig.viewer \
    | xargs -n 1 gcloud projects add-iam-policy-binding $PROJECT_ID \
      --member "serviceAccount:$VERIFY_ACCOUNT" \
      --condition None \
      --format "value(etag)" \
      --role 
    ```
**TODO: Grant write access to state bucket only**

1.  In the Cloud Console, go to **Cloud Build > Triggers**:

    [Open Triggers](https://console.cloud.google.com/cloud-build/triggers){ .md-button }

1.  Click **Create trigger** and configure the following settings:

    +   **Name**: `verify`
    +   **Region**: Select the region in which you created the connection.
    +   **Event**: **Pull request**
    +   **Source**: **2nd-gen**
    +   **Repository**: Select the Git repository that contains your
        Terraform configuration.
    +   **Branch**: `^master$` or `^main$`, depending on the name of your main branch.
    +   **Cloud Build configuration file location**: `cloudbuild.verify.yaml`
    +   **Require approval before build executes**: **disabled**
    +   **Send logs to GitHub**: **enabled**
    +   **Service account**: `jitgroups-cloudbuild-verify`



1.  Click **Create**.

1.  In your Git repository, create a build configuration file:

    ```sh
    touch cloudbuild.verify.yaml
    ```

1.  Open the file `cloudbuild.verify.yaml` and paste the following workflow configuration:

    ```yaml
    substitutions:
      _JITGROUPS_REF: 'TAG'
    steps:
    
    # Clone JIT Groups repository
    - name: 'alpine/git'
      args:
        - clone
        - https://github.com/GoogleCloudPlatform/jit-access.git
        - --branch
        - $_JITGROUPS_REF
        - target
    
    # Terraform: Init
    - name: 'hashicorp/terraform:1.9'
      args:
        - 'init'
        - '-no-color'
    
    # Terraform: Plan
    - name: 'hashicorp/terraform:1.9'
      args:
        - plan
        - -no-color
        - -input=false
    
    options:
      logging: CLOUD_LOGGING_ONLY
    ```

    Replace `JITGROUPS_REF` with the JIT Groups tag or branch that you want to deploy, for example `tags/2.0.0` or
    `jitgroups/latest`.

1.  Commit your changes:

    ```sh
    git add -A && git commit -m 'Add Cloud Build configuration for verifying pull requests'
    ```
       