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

variable "location" {
    description                = "Region of the parameter"
    type                       = string
    default                    = "global"
}

#------------------------------------------------------------------------------
# Required APIs.
#------------------------------------------------------------------------------

resource "google_project_service" "parametermanager" {
    project                    = var.project_id
    service                    = "parametermanager.googleapis.com"
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
# Parameter containing the policy.
#------------------------------------------------------------------------------

#
# Parameter to store the policy in.
#
resource "google_parameter_manager_regional_parameter" "policy" {
    depends_on                 = [ google_project_service.parametermanager ]
    project                    = var.project_id
    location                   = var.location
    parameter_id               = "jit-${var.name}"
    format                     = "YAML"
}

resource "google_parameter_manager_regional_parameter_version" "v1" {
    parameter                  = google_parameter_manager_regional_parameter.policy.parameter_id
    parameter_version_id       = formatdate("vYYYYMMDDhhmmss", timestamp())
    parameter_data             = var.policy
}

#
# Allow the environment service account to access the parameter.
#
resource "google_project_iam_member" "parameter_binding" {
    project                    = var.project_id
    role                       = "roles/parametermanager.parameterAccessor"
    member                     = "serviceAccount:${google_service_account.environment.email}"
}

#------------------------------------------------------------------------------
# Outputs.
#------------------------------------------------------------------------------

output "service_account" {
    description                = "Service account used by the environment"  
    value                      = google_service_account.environment.email
}
