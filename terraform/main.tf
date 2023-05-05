terraform {
  backend "gcs" {
    prefix = "sys"
    bucket = "terraform_state_jit-access_eb47"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "4.63.1"
    }
  }
}

data "google_organization" "this" {
  name = "kartverket.no"
}

data "google_project" "this" {
  name = var.project_name
}
