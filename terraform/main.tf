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
  domain = var.organization_domain
}

data "google_project" "this" {
}
