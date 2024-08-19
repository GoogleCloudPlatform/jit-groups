You can set up continuous deployment for JIT Groups and your environment policies.
Changes that you commit to Git are then automatically applied to Google Cloud. 

## Set up workload identity federation

To let a CI/CD system such as GitHub Actions authenticate Google Cloud, it's best to use 
[workload identity federation :octicons-link-external-16:](https://cloud.google.com/iam/docs/workload-identity-federation). 
By using workload identity federation, you can avoid the need to store and manage 
[service account keys :octicons-link-external-16:](https://cloud.google.com/iam/docs/service-accounts#service_account_keys).

To use workload identity federation, create a workload identity pool and provider:

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

1.  Create a new workload identity pool:

    ```sh
    gcloud iam workload-identity-pools create github \
      --location "global" \
      --description "GitHub" \
      --display-name "GitHub"
    ```

1.  Add a workload identity pool provider:    

    === "GitHub Actions"
    
        ```sh
        gcloud iam workload-identity-pools providers create-oidc actions \
          --location "global" \
          --workload-identity-pool github \
          --issuer-uri "https://token.actions.githubusercontent.com/" \
          --attribute-condition "assertion.repository == 'OWNER/REPOSITORY'" \
          --attribute-mapping \
            "google.subject = assertion.sub,
             google.groups = [] + 
               (assertion.ref == 'refs/heads/BRANCH' ? ['mainline'] : []) +
               (assertion.ref.startsWith('refs/pull/') ? ['pr'] : [])"
        ```
    
        Replace the following:
    
        +   `OWNER`: the name of your GitHub organization, or your GitHub username if you're deploying from 
            a personal repository
        +   `REPOSITORY`: the name of the GitHub repository
        +   `BRANCH`: the name of your mainline branch, typically `master` or `main`.

        The attribute condition helps ensure that only GitHub Actions from your repository are allowed
        to obtain Google credentials, and the attribute mapping does the following:

        +   Use the `sub` claim [from the GitHub OIDC token :octicons-link-external-16:](https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/about-security-hardening-with-openid-connect#understanding-the-oidc-token)
            as subject. 
        +   Add the 'mainline' group if the GitHub Action is deploying your mainline branch.
        +   Add the 'pr' group if the GitHub Action is deploying a pull request.

!!! tip

    You can use Terraform to create the workload identity pool and provider, but it might create a
    circular dependency between your Terraform module and the workload identity pool and provider. To
    avoid this circular dependency, use the `gcloud` tool instead.

## Configure the deployment

Configure a deployment pipeline so that it authenticates using workload identity federation and deploys
to your Google Cloud project:

=== "GitHub Actions"
    
    1.  In your Git repository, create a GitHub Actions workflow: 
    
        ```sh
        mkdir -p .github/workflows
        touch .github/workflows/deploy.yaml
        ```
        
    1.  Open the file `.github/workflows/deploy.yaml` and paste the following workflow configuration:
    
        ```yaml
        name: 'Deploy to JIT Groups'
        on: [push]
    
        env:
          JITGROUPS_REF: 'TAG'
          WLIF_PROJECT_NUMBER: 'PROJECT_NUMBER' 
          
        jobs:
          deploy:
            runs-on: ubuntu-latest
        
            # Allow the job to fetch a GitHub ID token
            permissions:
              contents: 'read'
              id-token: 'write'
        
            steps:
            - uses: 'actions/checkout@v4'
            - uses: 'hashicorp/setup-terraform@v3'
        
            - name: 'Authenticate to Google Cloud'
              id: auth
              uses: 'google-github-actions/auth@v2'
              with:
                create_credentials_file: true
                workload_identity_provider: 'projects/${{env.WLIF_PROJECT_NUMBER}}/locations/global/workloadIdentityPools/github/providers/actions'
        
            - name: 'Clone JIT Groups repository'
              id: jitgroups-clone
              run: git clone https://github.com/GoogleCloudPlatform/jit-access.git --branch ${{env.JITGROUPS_REF}} target
        
            - name: 'Terraform: Init'
              id: terraform-init
              run: terraform init -upgrade
        
            - name: 'Terraform: Plan'
              id: terraform-plan
              if: github.event_name == 'pull_request'
              run: terraform plan -no-color -input=false
        
            - name: 'Terraform: Apply'
              id: terraform-apply
              if: github.event_name != 'pull_request'
              run: terraform apply -no-color -input=false -auto-approve
        ```
        
        Replace the following:
    
        +   `JITGROUPS_REF`: the JIT Groups tag or branch that you want to deploy, for example `tags/2.0.0` or
            `jitgroups/latest`.
        +   `PROJECT_NUMBER`: the project number of your Google Cloud project. To look up the project number,
            run the following command:
    
            ```sh
            gcloud projects describe $(gcloud config get core/project) \
              --format "value(projectNumber)"
            ```
    1.  Grant full access to GitHub Actions that build the mainline branch and viewer access to GitHub Actions that
        build a pull request:
    
        ```sh
        PROJECT_ID=$(gcloud config get core/project)
        PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format "value(projectNumber)")
        WLIF_POOL=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github
        
        gcloud projects add-iam-policy-binding $PROJECT_ID \
          --member principalSet://iam.googleapis.com/$WLIF_POOL/group/mainline \
          --role "roles/storage.admin"
        gcloud projects add-iam-policy-binding $PROJECT_ID \
          --member principalSet://iam.googleapis.com/$WLIF_POOL/group/mainline \
          --role "roles/owner"

        gcloud projects add-iam-policy-binding $PROJECT_ID \
          --member principalSet://iam.googleapis.com/$WLIF_POOL/group/pr \
          --role "roles/storage.objectViewer"
        ```
    
    1.  Commit your changes:
    
        ```sh
        git add -A && git commit -m 'Add workflow'
        ```