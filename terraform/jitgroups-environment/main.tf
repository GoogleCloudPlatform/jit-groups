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
                               
variable "name" {              
    description                = "Name of the environment"
    type                       = string
}                              
                               
variable "policy" {            
    description                = "Policy, in YAML format"
    type                       = string
}

variable "application_service_account" {
    description                = "Email address of the applicartion service account"
    type                       = string
    validation {
        condition              = endswith(var.application_service_account, ".iam.gserviceaccount.com")
        error_message          = "application_service_account must be a service account email address"
    }
}                            
                               
variable "resource_scope" {    
    description                = "Project, folder, or organization that this environment corresponds to"
    type                       = string
                               
    validation {               
        condition              = var.resource_scope == "" || (
                                   startswith(var.resource_scope, "organizations/") || 
                                   startswith(var.resource_scope, "folders/") || 
                                   startswith(var.resource_scope, "projects/"))
        error_message          = "resource_scope must be in the format organizations/ID, folders/ID, or projects/ID"
    }                          
} 

#------------------------------------------------------------------------------
# Local variables.
#------------------------------------------------------------------------------

provider "google" {
    project                    = var.project_id
}

#------------------------------------------------------------------------------
# Required APIs.
#------------------------------------------------------------------------------

resource "google_project_service" "secretmanager" {
    project                    = var.project_id
    service                    = "secretmanager.googleapis.com"
    disable_on_destroy         = false
}

#------------------------------------------------------------------------------
# Environment service account.
#------------------------------------------------------------------------------

#
# Service account used by application.
#
data "google_service_account" "jitgroups" {
    account_id                 = var.application_service_account
}

#
# Service account used by environment.
#
resource "google_service_account" "environment" {
    project                    = var.project_id
    account_id                 = "jit-${var.name}"
    display_name               = "JIT Groups environment"
}

#
# Grant the application service account permission to impersonate.
#
resource "google_service_account_iam_member" "service_account_member" {
    service_account_id         = google_service_account.environment.name
    role                       = "roles/iam.serviceAccountTokenCreator"
    member                     = "serviceAccount:${data.google_service_account.jitgroups.email}"
}

#------------------------------------------------------------------------------
# Secret containing the policy.
#------------------------------------------------------------------------------

#
# Secret to store the policy in.
#
resource "google_secret_manager_secret" "policy" {
    depends_on                 = [ google_project_service.secretmanager ]
    secret_id                  = "jit-${var.name}"
    
    replication {
        auto {}
    }
}
resource "google_secret_manager_secret_version" "v1" {
    secret                     = google_secret_manager_secret.policy.id
    secret_data                = var.policy
}

#
# Allow the environment service account to access the secret.
#
resource "google_secret_manager_secret_iam_member" "secret_binding" {
    project                    = google_secret_manager_secret.policy.project
    secret_id                  = google_secret_manager_secret.policy.secret_id
    role                       = "roles/secretmanager.secretAccessor"
    member                     = "serviceAccount:${google_service_account.environment.email}"
}


#------------------------------------------------------------------------------
# IAM bindings for resource scope.
#------------------------------------------------------------------------------

#
# Project scope.
#
resource "google_project_iam_member" "resource_project_binding_projectiamadmin" {
    count                      = startswith(var.resource_scope, "projects/") ? 1 : 0
    project                    = substr(var.resource_scope, 9, -1)
    role                       = "roles/resourcemanager.projectIamAdmin"
    member                     = "serviceAccount:${google_service_account.environment.email}"
}

#
# Folder scope.
#
resource "google_folder_iam_member" "resource_folder_binding_projectiamadmin" {
    count                      = startswith(var.resource_scope, "folders/") ? 1 : 0
    folder                     = var.resource_scope
    role                       = "roles/resourcemanager.projectIamAdmin"
    member                     = "serviceAccount:${google_service_account.environment.email}"
}

#
# Organization scope.
#
resource "google_organization_iam_member" "resource_organization_binding_projectiamadmin" {
    count                      = startswith(var.resource_scope, "organizations/") ? 1 : 0
    org_id                     = substr(var.resource_scope, 14, -1)
    role                       = "roles/resourcemanager.projectIamAdmin"
    member                     = "serviceAccount:${google_service_account.environment.email}"
}

#------------------------------------------------------------------------------
# Outputs.
#------------------------------------------------------------------------------

output "service_account" {
    description                = "Service account used by the environment"  
    value                      = google_service_account.environment.email
}
