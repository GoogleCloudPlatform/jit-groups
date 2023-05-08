locals {
  jit_access_variables = {
    RESOURCE_SCOPE         = "organizations/${data.google_organization.this.id}"
    ELEVATION_DURATION     = "600"
    JUSTIFICATION_HINT     = "Relevant JIRA-sak eller begrunnelse"
    JUSTIFICATION_PATTERN  = ".*"
    IAP_BACKEND_SERVICE_ID = google_compute_region_backend_service.jit.id
  }
}

resource "google_cloud_run_v2_service" "main" {
  name     = var.project_name
  location = var.region
  project  = data.google_project.this.project_id

  template {
    service_account_name = "${var.project_name}-runtime@${data.google_project.this.project_id}.iam.gserviceaccount.com"
    spec {
      containers {
        image = var.image
        dynamic "env" {
          for_each = local.jit_access_variables
          content {
            name  = each.name
            value = each.value
          }
        }
      }
    }
  }
}

resource "google_cloud_run_service_iam_member" "authorize" {
  location = google_cloud_run_v2_service.main.location
  project  = google_cloud_run_v2_service.main.project
  service  = google_cloud_run_v2_service.main.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
