You can set up continuous deployment for JIT Groups and your environment policies
so that changes that you make in Git are automatically applied in Google Cloud. 

## Set up authentication

...add `jitgroups-githubactions-federation` module

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