# Example Terraform to install with Cloud Run and Corporate Certificates

## Usage

You will need to update local.tf to your region + org_id + billing_account_id
Also the `google_iap_brand` will need the email of your deployment SA
`google_compute_global_address` needs to be set to a static ip address the you defined in your dns a record
`google_compute_managed_ssl_certificate` will also need to be updated with your dns A record
The build-and-push.sh needs your region and projectID to push the image correctly and after that push you need to update jit.tf `google_cloud_run_v2_service` to reference the sha you just pushed as well as the `backend id` as an output.

## Key comments

Have added:

- corp proxy
- org policy exceptions
- artifact registry

## Updating the JIT app

Because the JIT application is submoduled there is a build and push script that wil update the submodule and use our custom docker file that adds CORP certs in to rebuild and push the image. There is however a manual step currently to update the image sha in the cloud run definition in terraform. We can just make it always point to latest, but we would still need to trigger a redeploy to pull the new image so I think pinning to a sha makes the most sense. This is also not part of the CICD pipeline for the same reason.
