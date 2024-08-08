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
    description = "Project ID"
    type = string
}

variable "location" {
    description = "AppEngine location, see https://cloud.google.com/about/locations#region"
    type = string
}

variable "admin_email" {
    description = "Contact email address, must be a Cloud Identity/Workspace user"
    type = string
}

variable "resource_scope" {
    description = "Project, folder, or organization that JIT Access can manage access for"
    type = string
    
    validation {
        condition     = (startswith(var.resource_scope, "organizations/") || 
                         startswith(var.resource_scope, "folders/") || 
                         startswith(var.resource_scope, "projects/"))
        error_message = "resource_scope must be in the format organizations/ID, folders/ID, or projects/ID"
    }
}

variable "customer_id" {
    description = "Cloud Identity/Workspace customer ID"
    type = string
    
    validation {
        condition     = startswith(var.customer_id, "C")
        error_message = "customer_id must be a valid customer ID, starting with C"
    }
}

variable "iap_users" {
    description                 = "Users and groups to allow IAP-access to the application, prefixed by 'user:', 'group:', or domain:"
    type                        = list(string)
    default                     = []
}

variable "options" {
    description                 = "Configuration options"
    type                        = map(string)
    default                     = {}
}

#------------------------------------------------------------------------------
# Local variables.
#------------------------------------------------------------------------------

provider "google" {
    project = var.project_id
}

locals {
    sources = "${path.module}/../../sources"
}


#------------------------------------------------------------------------------
# Required APIs
#------------------------------------------------------------------------------

resource "google_project_service" "cloudasset" {
    project                 = var.project_id
    service                 = "cloudasset.googleapis.com"
    disable_on_destroy      = false
}

resource "google_project_service" "cloudresourcemanager" {
    project                 = var.project_id
    service                 = "cloudresourcemanager.googleapis.com"
    disable_on_destroy      = false
}

resource "google_project_service" "iap" {
    project                 = var.project_id
    service                 = "iap.googleapis.com"
    disable_on_destroy      = false
}

resource "google_project_service" "containerregistry" {
    project                 = var.project_id
    service                 = "containerregistry.googleapis.com"
    disable_on_destroy      = false
}

resource "google_project_service" "iamcredentials" {
    project                 = var.project_id
    service                 = "iamcredentials.googleapis.com"
    disable_on_destroy      = false
}

resource "google_project_service" "admin" {
    project                 = var.project_id
    service                 = "admin.googleapis.com"
    disable_on_destroy      = false
}

#------------------------------------------------------------------------------
# Service account.
#------------------------------------------------------------------------------

#
# Service account used by application.
#
resource "google_service_account" "jitaccess" {
    project                 = var.project_id
    account_id              = "jitaccess"
    display_name            = "Just-In-Time Access"
}

#
# Grant the service account the Token Creator role so that it can sign JWTs.
#
resource "google_service_account_iam_member" "service_account_member" {
    service_account_id      = google_service_account.jitaccess.name
    role                    = "roles/iam.serviceAccountTokenCreator"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}

#
# Grant the GAE default service account access to Cloud Storage and Artifact Registry,
# required for deploying and building new versions.
#
resource "google_project_iam_member" "project_binding_createonpushwriter" {
    project                 = var.project_id
    role                    = "roles/artifactregistry.createOnPushWriter"
    member                  = "serviceAccount:${var.project_id}@appspot.gserviceaccount.com"
}
resource "google_project_iam_member" "project_binding_storageadmin" {
    project                 = var.project_id
    role                    = "roles/storage.admin"
    member                  = "serviceAccount:${var.project_id}@appspot.gserviceaccount.com"
}

#------------------------------------------------------------------------------
# IAM bindings for resource scope.
#------------------------------------------------------------------------------

#
# Project scope.
#
resource "google_project_iam_member" "resource_project_binding_cloudassetviewer" {
    count                   = startswith(var.resource_scope, "projects/") ? 1 : 0
    project                 = substr(var.resource_scope, 9, -1)
    role                    = "roles/cloudasset.viewer"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}
resource "google_project_iam_member" "resource_project_binding_securityadmin" {
    count                   = startswith(var.resource_scope, "projects/") ? 1 : 0
    project                 = substr(var.resource_scope, 9, -1)
    role                    = "roles/iam.securityAdmin"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}

#
# Folder scope.
#
resource "google_folder_iam_member" "resource_folder_binding_cloudassetviewer" {
    count                   = startswith(var.resource_scope, "folders/") ? 1 : 0
    folder                  = var.resource_scope
    role                    = "roles/cloudasset.viewer"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}
resource "google_folder_iam_member" "resource_folder_binding_securityadmin" {
    count                   = startswith(var.resource_scope, "folders/") ? 1 : 0
    folder                  = var.resource_scope
    role                    = "roles/iam.securityAdmin"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}

#
# Organization scope.
#
resource "google_organization_iam_member" "resource_organization_binding_cloudassetviewer" {
    count                   = startswith(var.resource_scope, "organizations/") ? 1 : 0
    org_id                  = substr(var.resource_scope, 14, -1)
    role                    = "roles/cloudasset.viewer"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}
resource "google_organization_iam_member" "resource_organization_binding_securityadmin" {
    count                   = startswith(var.resource_scope, "organizations/") ? 1 : 0
    org_id                  = substr(var.resource_scope, 14, -1)
    role                    = "roles/iam.securityAdmin"
    member                  = "serviceAccount:${google_service_account.jitaccess.email}"
}

#------------------------------------------------------------------------------
# IAP.
#------------------------------------------------------------------------------

#
# Create an OAuth consent screen for IAP.
#
resource "google_iap_brand" "iap_brand" {
    support_email           = var.admin_email
    application_title       = "JIT Access"
    project                 = var.project_id
}

#
# Create an OAuth client ID for IAP.
#
resource "google_iap_client" "iap_client" {
    display_name            = "JIT Access"
    brand                   = google_iap_brand.iap_brand.name
}

#
# Allow user to access IAP.
#
resource "google_iap_web_iam_binding" "iap_binding_users" {
    count                   = length(var.iap_users) > 0 ? 1 : 0
    project                 = var.project_id
    role                    = "roles/iap.httpsResourceAccessor"
    members                 = concat([ "user:${var.admin_email}" ], var.iap_users)
}

#------------------------------------------------------------------------------
# GAE application.
#------------------------------------------------------------------------------

#
# Create ZIP with Java source code.
#
data "archive_file" "sources_zip" {
    type                    = "zip"
    source_dir              = "${local.sources}"
    output_path             = "${path.module}/target/jitaccess-sources.zip"
}

#
# Upload ZIP file to the AppEngine storage bucket.
#
resource "google_storage_bucket_object" "appengine_sources_object" {
    name                    = "jitaccess.${data.archive_file.sources_zip.output_md5}.zip"
    bucket                  = google_app_engine_application.appengine_app.default_bucket
    source                  = data.archive_file.sources_zip.output_path
}

#
# Crate an AppEngine application.
#
resource "google_app_engine_application" "appengine_app" {
    project                 = var.project_id
    location_id             = var.location
    iap {
        enabled              = true
        oauth2_client_id     = google_iap_client.iap_client.client_id
        oauth2_client_secret = google_iap_client.iap_client.secret
    }
}

#
# Crate an AppEngine version from the uploaded source code.
#
resource "google_app_engine_standard_app_version" "appengine_app_version" {
    version_id                = "v1"
    service                   = "default"
    project                   = var.project_id
    runtime                   = "java17"
    instance_class            = "F2"
    service_account           = google_service_account.jitaccess.email
    env_variables             = merge(var.options, {
        "RESOURCE_SCOPE"      = var.resource_scope
        "RESOURCE_CATALOG"    = "AssetInventory"
        "RESOURCE_CUSTOMER_ID"= var.customer_id
    })
    threadsafe = true
    delete_service_on_destroy = true
    deployment {
        zip {
          source_url = "https://storage.googleapis.com/${google_app_engine_application.appengine_app.default_bucket}/${google_storage_bucket_object.appengine_sources_object.name}"
        }
    }
    entrypoint {
        shell = ""
    }
}

output "url" {
    description             = "URL to application"  
    value                   = "https://${google_app_engine_application.appengine_app.default_hostname}/"
}

output "service_account" {
    description             = "Service account used by the application"  
    value                   = google_service_account.jitaccess.email
}

