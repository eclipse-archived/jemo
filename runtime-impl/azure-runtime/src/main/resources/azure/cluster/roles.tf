resource "azurerm_role_assignment" "ra1" {
  scope                = "${data.azurerm_subnet.kubesubnet.id}"
  role_definition_name = "Network Contributor"
  principal_id         = "${data.azuread_service_principal.service-principal.id}"
}

resource "azurerm_role_assignment" "ra2" {
  scope                = "${azurerm_user_assigned_identity.jemo-identity.id}"
  role_definition_name = "Managed Identity Operator"
  principal_id         = "${data.azuread_service_principal.service-principal.id}"
  depends_on           = ["azurerm_user_assigned_identity.jemo-identity"]
}

resource "azurerm_role_assignment" "ra3" {
  scope                = "${azurerm_application_gateway.gateway.id}"
  role_definition_name = "Contributor"
  principal_id         = "${azurerm_user_assigned_identity.jemo-identity.principal_id}"
  depends_on           = ["azurerm_user_assigned_identity.jemo-identity", "azurerm_application_gateway.gateway"]
}

resource "azurerm_role_assignment" "ra4" {
  scope                = "${data.azurerm_resource_group.rg.id}"
  role_definition_name = "Reader"
  principal_id         = "${azurerm_user_assigned_identity.jemo-identity.principal_id}"
  depends_on           = ["azurerm_user_assigned_identity.jemo-identity", "azurerm_application_gateway.gateway"]
}

resource "azurerm_role_assignment" "ra5" {
  scope                = "${data.azurerm_key_vault.key_vault.id}"
  role_definition_name = "Contributor"
  principal_id         = "${azurerm_user_assigned_identity.jemo-identity.principal_id}"
  depends_on           = ["azurerm_user_assigned_identity.jemo-identity"]
}

resource "azurerm_key_vault_access_policy" "jemo_key_vault_access_policy" {
  vault_name          = "${var.key_vault_name}"
  resource_group_name = "${data.azurerm_resource_group.rg.name}"

  tenant_id = "${var.tenant_id}"
  object_id = "${azurerm_user_assigned_identity.jemo-identity.principal_id}"

  secret_permissions = [
    "get",
  ]
}