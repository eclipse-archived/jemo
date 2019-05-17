output "jemo_user_client_id" {
  value = "${azuread_service_principal.jemo-user.application_id}"
}
output "jemo_user_client_secret" {
  value = "${azuread_service_principal_password.principal_password.value}"
}

output "ECLIPSE_JEMO_AZURE_RESOURCE_GROUP" {
  value = "${azurerm_resource_group.rg.name}"
}
output "ECLIPSE_JEMO_AZURE_EVENTHUB" {
  value = "${azurerm_eventhub_namespace.ehn.name}"
}
output "ECLIPSE_JEMO_AZURE_DB" {
  value = "${azurerm_cosmosdb_account.db.name}"
}
output "ECLIPSE_JEMO_AZURE_STORAGE" {
  value = "${azurerm_storage_account.sa.name}"
}
output "ECLIPSE_JEMO_AZURE_LOG_WORKSPACE" {
  value = "${azurerm_log_analytics_workspace.log-workspace.name}"
}
output "ECLIPSE_JEMO_AZURE_KEYVAULT" {
  value = "${azurerm_key_vault.key-vault.name}"
}
