variable "terraform_user_client_id" {
  description = "Client id of the service principal with admin priviledges"
}

variable "terraform_user_client_secret" {
  description = "Client secret of the service principal with admin priviledges"
}

variable "tenant_id" {
  description = "The tenant id"
}

variable "subscription_id" {
  description = "The subscription id"
}

variable "region" {
  description = "A location that offers the AKS service (australiaeast, canadacentral, canadaeast, centralus, eastus, eastus2, japaneast, northeurope, southeastasia, southindia, uksouth, ukwest, westeurope, westus, westus2). For more read https://docs.microsoft.com/en-us/azure/aks/container-service-quotas#region-availability"
}

variable "resource-group" {
  default = "jemorg"
}

variable "log-workspace-location" {
  description = "A location that offers the Log Analytics service (australiaeast, australiasoutheast, australiacentral, canadacentral, eastus, southcentralus, westcentralus, westus2, northeurope, westeurope, southeastasia,   japaneast, centralindia, uksouth, francecentral, koreacentral). For more read https://azure.microsoft.com/en-us/pricing/details/monitor/"
}

variable "sku" {
  default = "PerGB2018"
}