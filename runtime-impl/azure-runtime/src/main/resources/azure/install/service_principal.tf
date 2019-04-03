data "azurerm_subscription" "current" {}

resource "azuread_application" "app" {
  name = "jemo-user"
  available_to_other_tenants = false
  oauth2_allow_implicit_flow = true
}

resource "random_string" "password" {
  length = 32
}

resource "azuread_service_principal" "jemo-user" {
  application_id = "${azuread_application.app.application_id}"
}

resource "azuread_service_principal_password" "principal_password" {
  service_principal_id = "${azuread_service_principal.jemo-user.id}"
  value                = "${random_string.password.result}"
  end_date             = "${timeadd(timestamp(), "${2 * (24 * 365)}h")}"
}

resource "azurerm_role_assignment" "service_principal" {
  scope                = "${data.azurerm_subscription.current.id}"
  role_definition_id   = "${azurerm_role_definition.jemo-role.id}"
  principal_id         = "${azuread_service_principal.jemo-user.id}"
}


resource "azurerm_role_definition" "jemo-role" {
  name               = "jemo-role"
  scope              = "${data.azurerm_subscription.current.id}"

  permissions {
    actions     = [
      "Microsoft.Resources/subscriptions/read",
      "Microsoft.Storage/storageAccounts/listKeys/action",
      "Microsoft.EventHub/namespaces/eventhubs/write",
      "Microsoft.EventHub/namespaces/AuthorizationRules/listKeys/action",
      "Microsoft.DocumentDB/databaseAccounts/listKeys/action",
      "Microsoft.DocumentDB/databaseAccounts/read",
      "Microsoft.OperationalInsights/workspaces/read",
      "Microsoft.Operationalinsights/workspaces/sharedkeys/read",
      "Microsoft.Authorization/roleAssignments/read",
      "Microsoft.Authorization/roleDefinitions/read",
      "Microsoft.Network/virtualNetworks/read",
      "Microsoft.ManagedIdentity/userAssignedIdentities/read",
      "Microsoft.KeyVault/vaults/read",
      "Microsoft.KeyVault/vaults/secrets/read",
      "Microsoft.KeyVault/vaults/secrets/write"
    ]

    data_actions = [
    ]
  }

  assignable_scopes = [
    "${data.azurerm_subscription.current.id}",
  ]
}
