resource "google_project_service_identity" "iap" {
  project  = var.project_id
  provider = google-beta
  service  = "iap.googleapis.com"
}

resource "google_iap_brand" "jit" {
  project           = var.project_id
  support_email     = var.support_email
  application_title = "Just-In-Time Access"
}

resource "google_iap_client" "jit" {
  display_name = "Just-In-Time Access"
  brand        = google_iap_brand.jit.name
}

resource "google_project_iam_member" "jit_iap" {
  project = var.project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_project_service_identity.iap.email}"
}
