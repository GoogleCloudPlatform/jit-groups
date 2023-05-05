variable "image" {
  type        = string
  description = "The full container image path"
}

variable "region" {
  type        = string
  description = "The GCP region to use"
  default     = "eu-north1"
}

variable "project_name" {
  type        = string
  description = "Name of the GCP project and application"
  default     = "jit-access"
}

variable "organization_domain" {
  type        = string
  description = "Domain name of organization in GCP"
  default     = "kartverket.no"
}

variable "dns_name" {
  type        = string
  description = "The FQDN for the JIT-Access portal"
  default     = "jit-access.skip.kartverket.no"
}
