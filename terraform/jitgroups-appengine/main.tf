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
    description                = "AppEngine location, see https://cloud.google.com/about/locations#region"
    type                       = string
}         

variable "customer_id" {       
    description                = "Cloud Identity/Workspace customer ID"
    type                       = string
                               
    validation {               
        condition              = startswith(var.customer_id, "C")
        error_message          = "customer_id must be a valid customer ID, starting with C"
    }                          
}                       
     
variable "primary_domain" {     
    description                = "Primary domain of the Cloud Identity/Workspace account"
    type                       = string
}      

variable "organization_id" {     
    description                = "Organization ID of the Google Cloud organization"
    type                       = string
}                               
                               
variable "groups_domain" {     
    description                = "Domain to use for JIT groups, this can be the primary or a secondary domain"
    type                       = string
    default                    = null
}    

variable "admin_email" {       
    description                = "Contact email address, must be a Cloud Identity/Workspace user"
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

variable "secret_location" {
    description                = "Region to replicate secrets to. If this variable is set, automatic replication is used."
    type                       = string
    default                    = null
}

#------------------------------------------------------------------------------
# Provider.
#------------------------------------------------------------------------------

terraform {
    provider_meta "google" {
        module_name = "cloud-solutions/jitgroups-appengine-deploy-v2.0"
    }
}

#------------------------------------------------------------------------------
# Local variables.
#------------------------------------------------------------------------------

locals {
    sources                    = "${path.module}/../../sources"
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
        dynamic "auto" {
            for_each = var.secret_location == null ? [1] : []
            content {}
        }
    
        dynamic "user_managed" {
            for_each = var.secret_location != null ? [1] : []
            content {
                replicas {
                    location = var.secret_location
                }
            }
        }
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
# AppEngine.
#------------------------------------------------------------------------------

#
# Initialize AppEngine.
#
resource "google_app_engine_application" "appengine_app" {
    project                    = var.project_id
    location_id                = var.location
    iap {
        enabled                = true
        oauth2_client_id       = google_iap_client.iap_client.client_id
        oauth2_client_secret   = google_iap_client.iap_client.secret
    }
}

#------------------------------------------------------------------------------
# AppEngine default service account.
#------------------------------------------------------------------------------

#
# Grant the GAE default service account access to Cloud Storage and Artifact Registry,
# required for deploying and building new versions.
#
# Force-remove Editor role bindings as it's unnecessarily broad.
#
resource "google_project_iam_member" "project_binding_appengine_createonpushwriter" {
    depends_on                 = [ google_app_engine_application.appengine_app ]
    project                    = var.project_id
    role                       = "roles/artifactregistry.createOnPushWriter"
    member                     = "serviceAccount:${var.project_id}@appspot.gserviceaccount.com"
}
resource "google_project_iam_member" "project_binding_appengine_storageadmin" {
    depends_on                 = [ google_app_engine_application.appengine_app ]
    project                    = var.project_id
    role                       = "roles/storage.admin"
    member                     = "serviceAccount:${var.project_id}@appspot.gserviceaccount.com"
}
resource "google_project_iam_member_remove" "project_binding_appengine_editor" {
    depends_on                 = [ google_app_engine_application.appengine_app ]
    project                    = var.project_id
    role                       = "roles/editor"
    member                     = "serviceAccount:${var.project_id}@appspot.gserviceaccount.com"
}
resource "time_sleep" "project_binding_appengine" {
    depends_on                = [
        google_project_iam_member.project_binding_appengine_createonpushwriter,
        google_project_iam_member.project_binding_appengine_storageadmin,
        google_project_iam_member_remove.project_binding_appengine_editor
    ]

    # Give IAM some time to process the IAM policy update before we use it.
    create_duration           = "10s"
}

#------------------------------------------------------------------------------
# Deploy GAE application.
#------------------------------------------------------------------------------

#
# Create ZIP with Java source code.
#
data "archive_file" "sources_zip" {
    type                       = "zip"
    source_dir                 = "${local.sources}"
    output_path                = "${path.module}/target/jitgroups-sources.zip"
}

#
# Upload ZIP file to the AppEngine storage bucket.
#
resource "google_storage_bucket_object" "appengine_sources_object" {
    name                       = "jitgroups.${data.archive_file.sources_zip.output_sha256}.zip"
    bucket                     = google_app_engine_application.appengine_app.default_bucket
    source                     = data.archive_file.sources_zip.output_path
}

#
# Crate an AppEngine version from the uploaded source code.
#
# Keep existing versions to allow rollback/traffic migration.
#
resource "google_app_engine_standard_app_version" "appengine_app_version" {
    depends_on                 = [ time_sleep.project_binding_appengine ]
    version_id                 = "rev-${substr(data.archive_file.sources_zip.output_sha256, 0, 16)}"
    service                    = "default"
    project                    = var.project_id
    runtime                    = "java17"
    instance_class             = "F2"
    service_account            = google_service_account.jitgroups.email
    env_variables              = merge({
      "RESOURCE_SCOPE"         = var.resource_scope
      "CUSTOMER_ID"            = var.customer_id
      "PRIMARY_DOMAIN"         = var.primary_domain
      "ORGANIZATION_ID"        = var.organization_id
      "GROUPS_DOMAIN"          = var.groups_domain
      "SMTP_HOST"              = var.smtp_host
      "SMTP_SENDER_ADDRESS"    = var.smtp_user
      "SMTP_USERNAME"          = var.smtp_user
      "SMTP_SECRET"            = "${google_secret_manager_secret.smtp.name}/versions/latest"
      "ENVIRONMENTS"           = join(",", var.environments)
    }, var.options)
    threadsafe                 = true
    noop_on_destroy            = true
    deployment {
        zip {
          source_url = "https://storage.googleapis.com/${google_app_engine_application.appengine_app.default_bucket}/${google_storage_bucket_object.appengine_sources_object.name}"
        }
    }
    entrypoint {
        shell                  = ""
    }

    timeouts {
        create = "10m"
        update = "10m"
        delete = "10m"
    }
}

#
# Force traffic to new version
#
resource "google_app_engine_service_split_traffic" "appengine_app_version" {
    service                    = google_app_engine_standard_app_version.appengine_app_version.service
    migrate_traffic            = false

    split {
        shard_by               = "IP"
        allocations            = {
            (google_app_engine_standard_app_version.appengine_app_version.version_id) = 1.0
        }
    }
}

#------------------------------------------------------------------------------
# Outputs.
#------------------------------------------------------------------------------

output "url" {
    description                = "URL to application"  
    value                      = "https://${google_app_engine_application.appengine_app.default_hostname}/"
}
output "service_account" {
    description                = "Service account used by the application"  
    value                      = google_service_account.jitgroups.email
}
