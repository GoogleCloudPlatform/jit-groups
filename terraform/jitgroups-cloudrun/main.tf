#
# Copyright 2024 Google LLC
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

#------------------------------------------------------------------------------
# Input variables.
#------------------------------------------------------------------------------

variable "project_id" {
    description                = "Project to deploy to"
    type                       = string
}

variable "location" {
    description                = "Cloud Run location, see https://cloud.google.com/run/docs/locations"
    type                       = string
}

variable "admin_email" {
    description                = "Contact email address, must be a Cloud Identity/Workspace user"
    type                       = string
}

variable "groups_domain" {
    description                = "Domain to use for JIT groups, this can be the primary or a secondary domain"
    type                       = string
}

variable "resource_scope" {
    description                = "JIT Access 1.x compatibility: Project, folder, or organization that JIT Access can manage access for"
    type                       = string

    default                    = "" # Disabled

    validation {
        condition              = var.resource_scope == "" || (
                                   startswith(var.resource_scope, "organizations/") ||
                                   startswith(var.resource_scope, "folders/") ||
                                   startswith(var.resource_scope, "projects/"))
        error_message          = "resource_scope must be in the format organizations/ID, folders/ID, or projects/ID"
    }
}

variable "environments" {
    description                = "Environment service accounts, prefixed with 'serviceAccount:"
    type                       = list(string)
    default                    = []

    validation {
        condition              = alltrue([for e in var.environments : startswith(lower(e), "serviceaccount:")])
        error_message          = "environments must use the format 'serviceAccount:jit-NAME@PROJECT.iam.gserviceaccount.com'"
    }
}

variable "customer_id" {
    description                = "Cloud Identity/Workspace customer ID"
    type                       = string

    validation {
        condition              = startswith(var.customer_id, "C")
        error_message          = "customer_id must be a valid customer ID, starting with C"
    }
}

variable "iap_users" {
    description                = "Users and groups to allow IAP-access to the application, prefixed with 'user:', 'group:', or domain:"
    type                       = list(string)
    default                    = []
}

variable "options" {
    description                = "Configuration options"
    type                       = map(string)
    default                    = {}
}

variable "smtp_user" {
    description                = "SMTP host"
    type                       = string
    default                    = null
}

variable "smtp_password" {
    description                = "SMTP password"
    type                       = string
    default                    = null
}

variable "smtp_host" {
    description                = "SMTP host"
    type                       = string
    default                    = "smtp.gmail.com"
}

variable "domain" {
    description                = "Fully-qualified domain name to use for the external load balancer."
    type                       = string
}

variable "image_tag" {
    description                = "Docker image tag to deploy. If not specified, the image is built from source."
    type                       = string
    default                    = null
}

#------------------------------------------------------------------------------
# Provider.
#------------------------------------------------------------------------------

terraform {
    provider_meta "google" {
        module_name = "cloud-solutions/jitgroups-cloudrun-deploy-v2.0"
    }
}

provider "google-beta" {
    project                    = var.project_id
}

#------------------------------------------------------------------------------
# Local variables.
#------------------------------------------------------------------------------

locals {
    sources                    = "${path.module}/../../sources"
    image_name                 = "${var.location}-docker.pkg.dev/${var.project_id}/jitgroups/jitgroups"

    # Effective image tag to use.
    image_tag                  = var.image_tag != null ? var.image_tag : data.external.git.result.sha
}

#
# Get current commit SHA.
#
data "external" "git" {
    program                    = [
        "sh", "-c", var.image_tag != null
            ? "echo {\\\"sha\\\": \\\"${var.image_tag}\\\"}"
            : "echo {\\\"sha\\\": \\\"$(git rev-parse HEAD)\\\"}"
    ]
    working_dir                = local.sources
}

#------------------------------------------------------------------------------
# Required APIs
#------------------------------------------------------------------------------

resource "google_project_service" "cloudasset" {
    project                    = var.project_id
    service                    = "cloudasset.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "iam" {
    project                    = var.project_id
    service                    = "iam.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "cloudresourcemanager" {
    project                    = var.project_id
    service                    = "cloudresourcemanager.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "iap" {
    project                    = var.project_id
    service                    = "iap.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "containerregistry" {
    project                    = var.project_id
    service                    = "containerregistry.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "iamcredentials" {
    project                    = var.project_id
    service                    = "iamcredentials.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "cloudidentity" {
    project                    = var.project_id
    service                    = "cloudidentity.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "groupssettings" {
    project                    = var.project_id
    service                    = "groupssettings.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "secretmanager" {
    project                    = var.project_id
    service                    = "secretmanager.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "artifactregistry" {
    project                    = var.project_id
    service                    = "artifactregistry.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "run" {
    project                    = var.project_id
    service                    = "run.googleapis.com"
    disable_on_destroy         = false
}

resource "google_project_service" "compute" {
    project                    = var.project_id
    service                    = "compute.googleapis.com"
    disable_on_destroy         = false
}

#------------------------------------------------------------------------------
# Project.
#------------------------------------------------------------------------------

data "google_project" "project" {
    project_id                 = var.project_id
}

#
# Force-remove Editor role from Compute Engine service account, if present.
#
resource "google_project_iam_member_remove" "project_binding_gce_default" {
    project                    = var.project_id
    role                       = "roles/editor"
    member                     = "serviceAccount:${data.google_project.project.number}-compute@developer.gserviceaccount.com"
}

#------------------------------------------------------------------------------
# App service account.
#------------------------------------------------------------------------------

#
# Service account used by application.
#
resource "google_service_account" "jitgroups" {
    depends_on                 = [ google_project_service.iam ]
    project                    = var.project_id
    account_id                 = "jitgroups"
    display_name               = "JIT Groups Application"
}

#
# Grant the service account the Token Creator role so that it can sign JWTs.
#
resource "google_service_account_iam_member" "service_account_member" {
    service_account_id         = google_service_account.jitgroups.name
    role                       = "roles/iam.serviceAccountTokenCreator"
    member                     = "serviceAccount:${google_service_account.jitgroups.email}"
}

#------------------------------------------------------------------------------
# IAP.
#------------------------------------------------------------------------------

#
# Create an OAuth consent screen for IAP.
#
resource "google_iap_brand" "iap_brand" {
    depends_on                 = [ google_project_service.iap ]
    project                    = var.project_id
    support_email              = var.admin_email
    application_title          = "JIT Groups"
    lifecycle {
        # This resource can't be deleted.
        prevent_destroy = true
    }
}

#
# Create an OAuth client ID for IAP.
#
resource "google_iap_client" "iap_client" {
    display_name               = "JIT Groups"
    brand                      = google_iap_brand.iap_brand.name
}

#
# Allow users to access IAP.
#
resource "google_project_iam_binding" "iap_binding_users" {
    project                    = var.project_id
    role                       = "roles/iap.httpsResourceAccessor"
    members                    = concat([ "user:${var.admin_email}" ], var.iap_users)
}

#------------------------------------------------------------------------------
# Secret containing SMTP password.
#------------------------------------------------------------------------------

#
# Create secret to store SMTP password.
#
resource "google_secret_manager_secret" "smtp" {
    depends_on                 = [ google_project_service.secretmanager ]
    secret_id                  = "smtp"

    replication {
        auto {}
    }
}

#
# Allow the service account to access the secret.
#
resource "google_secret_manager_secret_iam_member" "secret_binding" {
    project                    = google_secret_manager_secret.smtp.project
    secret_id                  = google_secret_manager_secret.smtp.secret_id
    role                       = "roles/secretmanager.secretAccessor"
    member                     = "serviceAccount:${google_service_account.jitgroups.email}"
}

#------------------------------------------------------------------------------
# Docker image.
#------------------------------------------------------------------------------

#
# Create a Docker repository.
#
resource "google_artifact_registry_repository" "registry" {
    depends_on                 = [ google_project_service.artifactregistry ]
    format                     = "DOCKER"
    repository_id              = "jitgroups"
    project                    = var.project_id
    location                   = var.location
}

#
# Build a Docker image if no tag was provided.
#
resource "null_resource" "docker_image" {
    depends_on                 = [google_artifact_registry_repository.registry]
    count                      = var.image_tag != null ? 0 : 1
    provisioner "local-exec" {
        command = join("&&", [
            "docker build -t ${local.image_name}:${local.image_tag} ${local.sources}",
            "docker push ${local.image_name}:${local.image_tag}"
        ])
        interpreter = ["bash", "-c"]
    }
}

#------------------------------------------------------------------------------
# Cloud Run.
#------------------------------------------------------------------------------

#
# Create a new revision of a Cloud Run service.
#
resource "google_cloud_run_v2_service" "service" {
    depends_on                 = [null_resource.docker_image, google_project_service.run]

    location                   = var.location
    name                       = "default"
    project                    = var.project_id
    ingress                    = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

    template {
        service_account        = google_service_account.jitgroups.email
        execution_environment  = "EXECUTION_ENVIRONMENT_GEN2"

        scaling {
            max_instance_count = 2
        }

        containers {
            image = "${local.image_name}:${local.image_tag}"

            dynamic "env" {
                for_each       =  merge({
                                   "IAP_VERIFY_AUDIENCE"    = "false"
                                   "RESOURCE_SCOPE"         = var.resource_scope
                                   "CUSTOMER_ID"            = var.customer_id
                                   "GROUPS_DOMAIN"          = var.groups_domain
                                   "SMTP_HOST"              = var.smtp_host
                                   "SMTP_SENDER_ADDRESS"    = var.smtp_user
                                   "SMTP_USERNAME"          = var.smtp_user
                                   "SMTP_SECRET"            = "${google_secret_manager_secret.smtp.name}/versions/latest"
                                   "ENVIRONMENTS"           = join(",", var.environments)
                                 }, var.options)
                content {
                    name        = env.key
                    value       = env.value
                }
            }
        }
    }
}

#------------------------------------------------------------------------------
# SSL certificate.
#------------------------------------------------------------------------------

#
# NB. Certificates take 5-10 minutes to be usable after resource creation.
#     To check the provisioning status of the certificate, run
#     gcloud compute ssl-certificates describe jitgroups
#
resource "google_compute_managed_ssl_certificate" "certificate" {
    depends_on                 = [ google_project_service.compute ]
    project                    = var.project_id
    name                       = "jitgroups"

    managed {
        domains                = [var.domain]
    }
}

#------------------------------------------------------------------------------
# Load balancer backend.
#------------------------------------------------------------------------------

#
# Create serverless NEG for the Cloud Run service.
#
resource "google_compute_region_network_endpoint_group" "neg" {
    depends_on                 = [ google_project_service.compute ]
    project                    = var.project_id
    name                       = "jitgroups-neg"
    network_endpoint_type      = "SERVERLESS"
    region                     = var.location
    cloud_run {
        service                = google_cloud_run_v2_service.service.name
    }
}

resource "google_compute_backend_service" "backend" {
    project                    = var.project_id
    name                       = "jitgroups-backend"
    load_balancing_scheme      = "EXTERNAL"
    backend {
        group = google_compute_region_network_endpoint_group.neg.id
    }
    iap {
        oauth2_client_id     = google_iap_client.iap_client.client_id
        oauth2_client_secret = google_iap_client.iap_client.secret
    }
}

#------------------------------------------------------------------------------
# Load balancer frontend.
#------------------------------------------------------------------------------

resource "google_compute_global_address" "ip" {
    depends_on                 = [ google_project_service.compute ]
    project                    = var.project_id
    name                       = "jitgroups"
    address_type               = "EXTERNAL"
}

resource "google_compute_url_map" "url_map" {
    project                    = var.project_id
    name                       = "jitgroups"
    default_service            = google_compute_backend_service.backend.id
}

resource "google_compute_target_https_proxy" "proxy" {
    project                    = var.project_id
    name                       = "jitgroups"
    url_map                    = google_compute_url_map.url_map.id
    ssl_certificates           = [google_compute_managed_ssl_certificate.certificate.name]
    depends_on                 = [google_compute_managed_ssl_certificate.certificate]
}

resource "google_compute_global_forwarding_rule" "google_compute_forwarding_rule" {
    project                    = var.project_id
    name                       = "jitgroups"
    load_balancing_scheme      = "EXTERNAL"
    port_range                 = "443"
    target                     = google_compute_target_https_proxy.proxy.id
    ip_address                 = google_compute_global_address.ip.id
}

#
# Force-create service identity. Enabling the IAP API should do that automatically,
# but it doesn't.
#
resource "google_project_service_identity" "iap" {
    provider = google-beta
    project                    = var.project_id
    service                    = "iap.googleapis.com"
}

#
# Allow IAP to access Cloud Run service.
#
resource "google_project_iam_binding" "iap_invoker" {
    depends_on                 = [ google_project_service.iap, google_project_service_identity.iap ]
    project                    = var.project_id
    role                       = "roles/run.invoker"
    members = [
        "serviceAccount:service-${data.google_project.project.number}@gcp-sa-iap.iam.gserviceaccount.com"
    ]
}

#------------------------------------------------------------------------------
# Outputs.
#------------------------------------------------------------------------------

output "url" {
    description                = "URL to application"
    value                      = "https://${var.domain}/"
}

output "ip" {
    description                = "IP address to point DNS record to"
    value                      = google_compute_global_address.ip.address
}

output "service_account" {
    description                = "Service account used by the application"
    value                      = google_service_account.jitgroups.email
}
