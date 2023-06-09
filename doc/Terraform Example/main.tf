# -------------------
# JIT Project
# -------------------
module "jit_project" {
  source                  = "terraform-google-modules/project-factory/google"
  random_project_id       = true
  name                    = "just-in-time-access"
  org_id                  = locals.org_id
  billing_account         = local.billing_account
  default_service_account = "deprivilege"

  activate_apis = [
    "cloudasset.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iap.googleapis.com",
    "artifactregistry.googleapis.com",
    "run.googleapis.com",
    "compute.googleapis.com",
  ]
}

# -----------------
# SERVICE ACCOUNTS
# -----------------

# Note: this sa also needs group reader super admin rights
# https://cloud.google.com/architecture/manage-just-in-time-privileged-access-to-project#grant_access_to_allow_the_application_to_resolve_group_memberships
resource "google_service_account" "jit-sa" {
  project = module.jit_project.project_id

  account_id   = "jitaccess"
  display_name = "Service Account for just-in-time access"
}

# Access needed in the project
resource "google_project_iam_member" "jit-sa-project-access" {
  for_each = toset([
    "roles/clouddebugger.agent",
  ])
  project = module.jit_project.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.jit-sa.email}"
}

# Access needed accross the org for JIT SA
resource "google_organization_iam_member" "jit-sa-org-access" {
  for_each = toset([
    "roles/iam.securityAdmin",
    "roles/cloudasset.viewer",
  ])
  org_id = local.org_id
  role   = each.value
  member = "serviceAccount:${google_service_account.jit-sa.email}"
}
