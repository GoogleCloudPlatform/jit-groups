########################################################################################################################
#
# The outputs
#
########################################################################################################################

output "brand-name" {
  description = "Used to reimport incase of deletion from the statefile. Note this resource can not be deleted."
  value       = google_iap_brand.project_brand.name
}

output "ensure-cloudrun-env-IAP_BACKEND_SERVICE_ID" {
  description = "value needed for cloud run to reach the backend"
  value       = google_compute_backend_service.jit-backend.generated_id
}
