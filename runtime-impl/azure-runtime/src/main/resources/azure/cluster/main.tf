provider "azurerm" {
  version = "=1.21.0"

  subscription_id = "${var.subscription_id}"
  client_id       = "${var.terraform_user_client_id}"
  client_secret   = "${var.terraform_user_client_secret}"
  tenant_id       = "${var.tenant_id}"
}

data "azurerm_resource_group" "rg" {
  name = "${var.resource_group_name}"
}

data "azuread_service_principal" "service-principal" {
  application_id = "${var.jemo_user_client_id}"
}

data "azurerm_key_vault" key_vault {
  name          = "${var.key_vault_name}"
  resource_group_name = "${data.azurerm_resource_group.rg.name}"
}

data "azurerm_virtual_network" vn {
  name = "${var.virtual_network_name}"
  resource_group_name  = "${data.azurerm_resource_group.rg.name}"
  _DEPENDS_ON_VN_
}

data "azurerm_subnet" "kubesubnet" {
  name                 = "${data.azurerm_virtual_network.vn.subnets[0]}"
  virtual_network_name = "${var.virtual_network_name}"
  resource_group_name  = "${data.azurerm_resource_group.rg.name}"
}

data "azurerm_subnet" "appgwsubnet" {
  name                 = "${data.azurerm_virtual_network.vn.subnets[1]}"
  virtual_network_name = "${var.virtual_network_name}"
  resource_group_name  = "${data.azurerm_resource_group.rg.name}"
}

