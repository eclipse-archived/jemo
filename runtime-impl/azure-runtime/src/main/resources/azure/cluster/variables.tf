variable "resource_group_name" {
  description = "Name of the resource group already created."
  default = "jemorg"
}

variable "jemo_user_client_id" {
  description = "Application ID/Client ID  of the service principal. Used by AKS to manage AKS related resources on Azure like vms, subnets."
}

variable "jemo_user_client_secret" {
  description = "Secret of the service principal. Used by AKS to manage Azure."
}

variable "terraform_user_client_id" {
  description = "Client id of service principal with admin priviledges"
}

variable "terraform_user_client_secret" {
  description = "Client secret of service principal with admin priviledges"
}

variable "tenant_id" {
  description = "The tenant id"
}

variable "subscription_id" {
  description = "The subscription id"
}

variable "key_vault_name" {
  description = "The Jemo key vault name."
  default = "jemokv"
}

variable "virtual_network_name" {
  description = "Virtual network name"
  default     = "jemo-virtual-network"
}

variable "public_ip_name" {
  description = "The public ip name"
  default     = "jemo-public-ip"
}

variable "virtual_network_address_prefix" {
  description = "Containers DNS server IP address."
  default     = "15.0.0.0/8"
}

variable "aks_subnet_name" {
  description = "AKS Subnet Name."
  default     = "jemo-subnet"
}

variable "aks_subnet_address_prefix" {
  description = "Containers DNS server IP address."
  default     = "15.0.0.0/16"
}

variable "app_gateway_subnet_name" {
  description = "The App Gateway Subnet Name."
  default     = "appgwsubnet"
}

variable "app_gateway_subnet_address_prefix" {
  description = "Containers DNS server IP address."
  default     = "15.1.0.0/16"
}

variable "app_gateway_name" {
  description = "Name of the Application Gateway."
  default = "jemo-app-gateway"
}

variable "app_gateway_sku" {
  description = "Name of the Application Gateway SKU."
  default = "Standard_v2"
}

variable "app_gateway_tier" {
  description = "Tier of the Application Gateway SKU."
  default = "Standard_v2"
}


variable "aks_name" {
  description = "Name of the AKS cluster."
  default     = "jemo-cluster"
}
variable "aks_dns_prefix" {
  description = "Optional DNS prefix to use with hosted Kubernetes API server FQDN."
  default     = "jemo"
}

variable "aks_agent_os_disk_size" {
  description = "Disk size (in GB) to provision for each of the agent pool nodes. This value ranges from 0 to 1023. Specifying 0 will apply the default disk size for that agentVMSize."
  default     = 30
}

variable "aks_agent_count" {
  description = "The number of agent nodes for the cluster."
  default     = 2
}

variable "aks_agent_vm_size" {
  description = "The size of the Virtual Machine."
  default     = "Standard_D1_v2"
}

variable "aks_service_cidr" {
  description = "A CIDR notation IP range from which to assign service cluster IPs."
  default     = "10.0.0.0/16"
}

variable "aks_dns_service_ip" {
  description = "Containers DNS server IP address."
  default     = "10.0.0.10"
}

variable "aks_docker_bridge_cidr" {
  description = "A CIDR notation IP for Docker bridge."
  default     = "172.17.0.1/16"
}

variable "tags" {
  type = "map"

  default = {
    source = "terraform"
  }
}
