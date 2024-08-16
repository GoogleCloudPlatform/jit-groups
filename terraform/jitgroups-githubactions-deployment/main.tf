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

variable "github_owner" {
    description                = "Name of the GitHub organization or username"
    type                       = string
}                   

variable "github_repository" {
    description                = "Name of the GitHub repository, without organization/user prefix"
    type                       = string
}

variable "allowed_branch" {
    description                = "Branch to allow deployments from"
    type                       = string
    default                    = "master"
}                   


#------------------------------------------------------------------------------
# Local variables.
#------------------------------------------------------------------------------

provider "google" {
    project = var.project_id
}

#------------------------------------------------------------------------------
# Workload identity federation.
#------------------------------------------------------------------------------

resource "google_iam_workload_identity_pool" "pool" {
    project                    = var.project_id
    workload_identity_pool_id  = "github-actions"
}

resource "google_iam_workload_identity_pool_provider" "oidc" {
    workload_identity_pool_provider_id = "oidc"

    project                    = var.project_id
    workload_identity_pool_id  = google_iam_workload_identity_pool.pool.workload_identity_pool_id
    oidc {
        issuer_uri             = "https://token.actions.githubusercontent.com/"
    }
    
    #
    # Only allow token exchanges for the GitHub Actions of a single repository.
    #
    attribute_condition        = join("&&", [
        "assertion.repository_owner == '${var.github_owner}'",
        "assertion.repository       == '${var.github_repository}'"
    ])
    
    #
    # Include repository name and ref in the subject so that we can limit
    # access to specific branches.
    #
    attribute_mapping          = {
        "google.subject"       = "assertion.repository + '@' + assertion.ref"
    }
}

data "google_project" "project" {
    project_id                 = var.project_id
}

#------------------------------------------------------------------------------
# Outputs.
#------------------------------------------------------------------------------

output "principal" {
    description                = "Principal that idenfifies GitHub actions"  
    value                      = join("", [
        "principal://iam.googleapis.com",
        "/projects/${data.google_project.project.number}",
        "/locations/global",
        "/workloadIdentityPools/${google_iam_workload_identity_pool.pool.workload_identity_pool_id}",
        "/subject/${var.github_repository}@refs/heads/${var.allowed_branch}"
    ])
}
