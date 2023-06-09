# -------------------
# Artifact Registry
# -------------------

resource "google_project_service_identity" "artifact_registry_service_agent" {
  provider = google-beta
  project  = module.jit_project.project_id
  service  = "artifactregistry.googleapis.com"
}

resource "google_artifact_registry_repository" "docker" {
  depends_on = [
    google_project_iam_member.artifact_kms
  ]
  location      = local.region
  repository_id = "docker"
  description   = "Runner containers registry"
  format        = "DOCKER"
  project       = module.jit_project.project_id
}

data "google_iam_policy" "registry_viewers" {
  binding {
    role    = "roles/artifactregistry.reader"
    members = ["serviceAccount:${google_service_account.jit-sa.email}"]
  }
}

resource "google_artifact_registry_repository_iam_policy" "policy" {
  provider    = google-beta
  project     = google_artifact_registry_repository.docker.project
  location    = google_artifact_registry_repository.docker.location
  repository  = google_artifact_registry_repository.docker.name
  policy_data = data.google_iam_policy.registry_viewers.policy_data
}
