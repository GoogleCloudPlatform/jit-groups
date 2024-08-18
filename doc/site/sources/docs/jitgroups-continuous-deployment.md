You can set up continuous deployment for JIT Groups and your environment policies.
Changes that you commit to Git are then automatically applied to Google Cloud. 

## Configure workload identity federation

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
        gcloud iam workload-identity-pools providers create-oidc github-actions \
          --location "global" \
          --workload-identity-pool github \
          --issuer-uri "https://token.actions.githubusercontent.com/" \
          --attribute-mapping "google.subject = assertion.repository + '@' + assertion.ref" \
          --attribute-condition "assertion.repository_owner == 'OWNER' && assertion.repository == 'REPOSITORY'"
        ```
    
        Replace the following:
    
        +   `OWNER`: the name of your GitHub organization, or your GitHub username if you're deploying from 
            a personal repository
        +   `REPOSITORY`: the name of the GitHub repository.
    
        The attribute mapping uses the 
        [claims from the GitHub OIDC token :octicons-link-external-16:](https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/about-security-hardening-with-openid-connect#understanding-the-oidc-token)
        to derive a subject that combines the repository name and branch name, for example `google/guava@refs/heads/main`.

        The attribute condition helps ensure that only GitHub Actions from the `OWNER/REPOSITORY` are allowed
        to obtain Google Cloud credentials.

!!! note

    You can use Terraform to create the workload identity pool and provider, but it might create a
    circular dependency between your Terraform module and the workload identity pool and provider. To
    avoid this circular dependency, use the `gcloud` tool instead.

## Add a workflow

=== "GitHub Actions"



```yaml
jobs:
  job_id:
    # Allow the job to fetch a GitHub ID token
    permissions:
      contents: 'read'
      id-token: 'write'

    steps:
    - uses: 'actions/checkout@v4'

    - id: 'auth'
      name: 'Authenticate to Google Cloud'
      uses: 'google-github-actions/auth@v2'
      with:
        create_credentials_file: true
        workload_identity_provider: 'projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-actions/providers/OIDC'
```

Replace `PROJECT_NUMBER` with the project number of the project.

!!!note

    You must use the project number