# Access to reach the application via IAP
resource "google_iap_web_iam_binding" "binding" {
  project = module.jit_project.project_id
  role    = "roles/iap.httpsResourceAccessor"
  members = [
    "allAuthenticatedUsers", #let everybody reach the app. You still need perms to JIT elevate
    # Note this requires temporarily disabling `constraints/iam.allowedPolicyMemberDomains` before you can apply
  ]
}


# Deploy cloud run app
resource "google_cloud_run_v2_service" "jit-app" {
  project  = module.jit_project.project_id
  name     = "jitaccess"
  location = local.region

  template {
    service_account = google_service_account.jit-sa.email
    containers {
      image = "${local.region}-${docker.pkg.dev}/${module.jit_project.project_id}/${google_artifact_registry_repository.docker.name}@sha:439e05f49b46401b5c0a8173e0791add1c6c234d8d9a08d85dd4137ee0273ffc"

      env {
        name  = "RESOURCE_SCOPE"
        value = "organizations/${local.org_id}"
      }

      env {
        name  = "ELEVATION_DURATION" #max value, users have a slider for less
        value = "60"                 #minutes
      }

      env {
        name  = "JUSTIFICATION_HINT"
        value = "Bug or case number"
      }

      env {
        name  = "JUSTIFICATION_PATTERN"
        value = ".*"
      }

      env {
        #This has to be hardcoded as the back end service is dependant on this terraform resource, and so if this is paramaterised then we have circular dependencies
        # Accessable from the terraform output 'backend_id'
        name  = "IAP_BACKEND_SERVICE_ID"
        value = "xxxxxxxxx"
      }

      env {
        name  = "http_proxy"
        value = "http://proxy.corp:8080" #change to your proxy server
      }

      env {
        name  = "https_proxy"
        value = "http://proxy.corp:8080" #change to your proxy server
      }

    }
  }
}

########################################################################################################################
#
# Backend Networking
#
########################################################################################################################


# Create a serverless network endpoint group for the Cloud Run service and connect it to the backend service
resource "google_compute_region_network_endpoint_group" "cloudrun_neg" {
  project               = module.jit_project.project_id
  name                  = "cloudrun-neg"
  network_endpoint_type = "SERVERLESS"
  region                = local.region
  cloud_run {
    service = google_cloud_run_v2_service.jit-app.name
  }
}

#set of vm's to handle load ballancing
resource "google_compute_backend_service" "jit-backend" {
  project               = module.jit_project.project_id
  name                  = "jitaccess-backend"
  load_balancing_scheme = "EXTERNAL"
  backend {
    group = google_compute_region_network_endpoint_group.cloudrun_neg.id
  }
  iap {
    oauth2_client_id     = google_iap_client.project_client.client_id
    oauth2_client_secret = google_iap_client.project_client.secret
  }

}


########################################################################################################################
#
# Front End
#
########################################################################################################################

# external ip address for the front end to be hosted on
# There is now a DNS A record for `jitaccess.corp.com` -> 34.xxx.xxx.xxx
# As such this address needs to be static
resource "google_compute_global_address" "jitaccess-ip" {
  project      = module.jit_project.project_id
  name         = "jitaccess-ip"
  address_type = "EXTERNAL"
  address      = "34.xxx.xxx.xxx"
}

# use to route requests to back end services, can have rules
resource "google_compute_url_map" "lb_default" {
  project         = module.jit_project.project_id
  name            = "jitaccess"
  default_service = google_compute_backend_service.jit-backend.id
}

# google managed ssl certificates, to check the status run the following
# gcloud compute ssl-certificates describe jitaccess \
#     --global \
#     --format="get(name,managed.status)"
resource "google_compute_managed_ssl_certificate" "lb_default" {
  provider = google-beta
  project  = module.jit_project.project_id
  name     = "jitaccess"

  managed {
    domains = ["jitaccess.corp.com"] #this needs to be the url that you configured as your DNS A record
  }
}

# maps incomming https requests to a url map.
resource "google_compute_target_https_proxy" "lb_default" {
  provider = google-beta
  project = module.jit_project.project_id
  name    = "jitaccess-https-proxy"
  url_map = google_compute_url_map.lb_default.id
  ssl_certificates = [
    google_compute_managed_ssl_certificate.lb_default.name
  ]
  depends_on = [
    google_compute_managed_ssl_certificate.lb_default
  ]
}


# forwards packets matching the ip and port range to a pool of vms.
resource "google_compute_global_forwarding_rule" "google_compute_forwarding_rule" {
  project               = module.jit_project.project_id
  name                  = "jitaccess-https"
  provider              = google-beta
  load_balancing_scheme = "EXTERNAL"
  port_range            = "443"
  target                = google_compute_target_https_proxy.lb_default.id
  ip_address            = google_compute_global_address.jitaccess-ip.id
  depends_on = [
    google_tags_tag_binding.binding
  ]
}

########################################################################################################################
#
# Tag to allow access through the org policy
#
########################################################################################################################

# Tag group
resource "google_tags_tag_key" "key" {
  parent      = "organizations/${local.org_id}"
  short_name  = "jit-tag"
  description = "Allow JIT an external load balancer."
}

# tag instance
resource "google_tags_tag_value" "value" {
  parent      = "tagKeys/${google_tags_tag_key.key.name}"
  short_name  = "allowed"
  description = "jit LB allow tag."
}

# bind tag to this project - note this is also harcoded into the org-policy
resource "google_tags_tag_binding" "binding" {
  depends_on = [
    module.jit_project
  ]
  parent    = "//cloudresourcemanager.googleapis.com/projects/${module.jit_project.project_number}"
  tag_value = "tagValues/${google_tags_tag_value.value.name}"
}

# Prevent external loadbalancer and only allow this one specific LB to be external
resource "google_org_policy_policy" "restrict_lb_creation_by_lb_types" {
  name   = "organizations/${local.org_id}/policies/compute.restrictLoadBalancerCreationForTypes"
  parent = "organizations/${local.org_id}"
  spec {
    rules {
      condition {
        description = "allowing JIT access an external LB"
        title       = "Jit access"
        expression  = "resource.matchTag('${local.org_id}/jit-tag', 'allowed')"
      }
      values {
        allowed_values = ["EXTERNAL_HTTP_HTTPS"]
      }
    }

    rules {
      values {
        allowed_values = ["in:INTERNAL"]
      }
    }
  }
}

########################################################################################################################
#
# Identity Aware Proxy
#
########################################################################################################################

resource "google_iap_brand" "project_brand" {
  support_email     = "YOUR EMAIL HERE"
  application_title = "Cloud IAP protected Application"
  project           = module.jit_project.project_number
  lifecycle {
    # This resource has no delete endpoint, so if its deleted it will only be removed in the state file
    prevent_destroy = true
  }
}

# To run this needs to download https://www.gstatic.com/iap/verify/public_key-jwk, as such we need a dns record to point gstatic.com -> private.googleapis.com
resource "google_iap_client" "project_client" {
  display_name = "jit client"
  brand        = google_iap_brand.project_brand.name
}

# Enable the IAP service account. Becuase just enabling the api doesnt cause it to be created
resource "google_project_service_identity" "iap_sa" {
  provider = google-beta

  project = module.jit_project.project_id
  service = "iap.googleapis.com"
}

# allow iap SA to invoke cloud run
resource "google_project_iam_binding" "iap_sa_run_invoker" {
  project = module.jit_project.project_id
  role    = "roles/run.invoker"

  members = [
    "serviceAccount:service-${module.jit_project.project_number}@gcp-sa-iap.iam.gserviceaccount.com"
  ]
}