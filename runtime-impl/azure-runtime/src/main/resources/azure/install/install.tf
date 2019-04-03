provider "azurerm" {
  version = "=1.21.0"

  subscription_id = "${var.subscription_id}"
  client_id       = "${var.terraform_user_client_id}"
  client_secret   = "${var.terraform_user_client_secret}"
  tenant_id       = "${var.tenant_id}"
}

provider "azuread" {
  version = "=0.1.0"

  subscription_id = "${var.subscription_id}"
  client_id       = "${var.terraform_user_client_id}"
  client_secret   = "${var.terraform_user_client_secret}"
  tenant_id       = "${var.tenant_id}"
}

resource "azurerm_resource_group" "rg" {
  name     = "${var.resource-group}"
  location = "${var.region}"
}

resource "random_string" "random-suffix" {
  length = 17
  special = false
  upper = false
}

resource "azurerm_storage_account" "sa" {
  name                     = "jemosa${random_string.random-suffix.result}"
  resource_group_name      = "${azurerm_resource_group.rg.name}"
  location                 = "${var.region}"
  account_tier             = "Standard"
  account_replication_type = "GRS"

  depends_on = ["azurerm_resource_group.rg"]
}

resource "azurerm_cosmosdb_account" "db" {
  name                = "jemocdba-${random_string.random-suffix.result}"
  location            = "${azurerm_resource_group.rg.location}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  offer_type          = "Standard"
  kind                = "GlobalDocumentDB"

  enable_automatic_failover = true

  consistency_policy {
    consistency_level       = "BoundedStaleness"
    max_interval_in_seconds = 10
    max_staleness_prefix    = 200
  }

  geo_location {
    prefix            = "jemocdba-${random_string.random-suffix.result}-cid"
    location          = "${azurerm_resource_group.rg.location}"
    failover_priority = 0
  }

  depends_on = ["azurerm_resource_group.rg"]
}

resource "azurerm_eventhub_namespace" "ehn" {
  name                = "jemoehn-${random_string.random-suffix.result}"
  location            = "${azurerm_resource_group.rg.location}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  sku                 = "Standard"
  capacity            = 1
  kafka_enabled       = false

  depends_on = ["azurerm_resource_group.rg"]
}

resource "azurerm_log_analytics_workspace" "log-workspace" {
  name                = "jemo-log-workspace-${random_string.random-suffix.result}"
  location            = "${var.log-workspace-location}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  sku                 = "${var.sku}"
  retention_in_days   = 30

  depends_on = ["azurerm_resource_group.rg"]
}

resource "azurerm_key_vault" "key-vault" {
  name                        = "jemokv-${random_string.random-suffix.result}"
  location                    = "${azurerm_resource_group.rg.location}"
  resource_group_name         = "${azurerm_resource_group.rg.name}"
  tenant_id                   = "${var.tenant_id}"

  sku {
    name = "standard"
  }

  access_policy {
    tenant_id = "${var.tenant_id}"
    object_id = "${azuread_service_principal.jemo-user.id}"

    certificate_permissions = [
    ]

    key_permissions = [
    ]

    secret_permissions = [
      "get",
      "list",
    ]
  }

  depends_on = ["azurerm_resource_group.rg", "azurerm_role_assignment.service_principal"]
}

data "azuread_service_principal" terraform-user {
  application_id = "${var.terraform_user_client_id}"
}

resource "azurerm_key_vault_access_policy" "key-vault-access-policy" {
  vault_name          = "${azurerm_key_vault.key-vault.name}"
  resource_group_name = "${azurerm_key_vault.key-vault.resource_group_name}"

  tenant_id = "${var.tenant_id}"
  object_id = "${data.azuread_service_principal.terraform-user.id}"

  certificate_permissions = [
  ]

  key_permissions = [
  ]

  secret_permissions = [
    "get",
    "list",
    "set",
    "delete",
  ]

  depends_on = ["azurerm_key_vault.key-vault"]
}

resource "azurerm_key_vault_secret" "clientId" {
  name     = "clientId"
  value    = "${azuread_service_principal.jemo-user.application_id}"
  vault_uri = "${azurerm_key_vault.key-vault.vault_uri}"
  depends_on = ["azurerm_key_vault_access_policy.key-vault-access-policy"]
}

resource "azurerm_key_vault_secret" "clientSecret" {
  name     = "clientSecret"
  value    = "${random_string.password.result}"
  vault_uri = "${azurerm_key_vault.key-vault.vault_uri}"
  depends_on = ["azurerm_key_vault_access_policy.key-vault-access-policy"]
}

resource "azurerm_key_vault_secret" "tenantId" {
  name     = "tenantId"
  value    = "${var.tenant_id}"
  vault_uri = "${azurerm_key_vault.key-vault.vault_uri}"
  depends_on = ["azurerm_key_vault_access_policy.key-vault-access-policy"]
}

resource "azurerm_key_vault_secret" "encryption-key" {
  name     = "encryptionKey"
  value    = "${random_string.encryption-key.result}"
  vault_uri = "${azurerm_key_vault.key-vault.vault_uri}"
  depends_on = ["azurerm_key_vault_access_policy.key-vault-access-policy"]
}

resource "random_string" "encryption-key" {
  length = 16
}
