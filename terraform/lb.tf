resource "google_compute_global_address" "jit" {
  project = var.project_id
  name    = local.jit_name
}

resource "google_compute_managed_ssl_certificate" "jit" {
  project = var.project_id
  name    = var.project_name
  managed {
    domains = [var.dns_name]
  }
}

resource "google_compute_region_network_endpoint_group" "jit" {
  project               = var.project_id
  name                  = var.project_name
  network_endpoint_type = "SERVERLESS"
  region                = var.region
  cloud_run {
    service = var.project_name
  }
}

resource "google_compute_region_backend_service" "jit" {
  project = var.project_id
  name    = "${var.project_name}-backend"
  region  = var.region

  protocol    = "HTTP"
  port_name   = "http"
  timeout_sec = 30

  iap {
    oauth2_client_id     = google_iap_client.jit.client_id
    oauth2_client_secret = google_iap_client.jit.secret
  }

  backend {
    group = google_compute_region_network_endpoint_group.jit.id
  }
}

resource "google_compute_url_map" "jit" {
  project         = var.project_id
  name            = var.project_name
  default_service = google_compute_region_backend_service.jit.id
}

resource "google_compute_target_https_proxy" "jit" {
  project = var.project_id
  name    = var.project_name
  url_map = google_compute_url_map.jit.id

  ssl_certificates = [
    google_compute_managed_ssl_certificate.jit.id
  ]
}

resource "google_compute_global_forwarding_rule" "jit" {
  project    = var.project_id
  name       = var.project_name
  target     = google_compute_target_https_proxy.jit.id
  port_range = "443"
  ip_address = google_compute_global_address.jit.address
}
