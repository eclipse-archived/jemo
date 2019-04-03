output "jemo_user_client_id" {
  value = "${azuread_service_principal.jemo-user.application_id}"
}
output "jemo_user_client_secret" {
  value = "${azuread_service_principal_password.principal_password.value}"
}

output "eclipse.jemo.azure.resourcegroup" {
  value = "${azurerm_resource_group.rg.name}"
}
output "eclipse.jemo.azure.eventhub" {
  value = "${azurerm_eventhub_namespace.ehn.name}"
}
output "eclipse.jemo.azure.db" {
  value = "${azurerm_cosmosdb_account.db.name}"
}
output "eclipse.jemo.azure.storage" {
  value = "${azurerm_storage_account.sa.name}"
}
output "eclipse.jemo.azure.log-workspace" {
  value = "${azurerm_log_analytics_workspace.log-workspace.name}"
}
output "eclipse.jemo.azure.keyvault" {
  value = "${azurerm_key_vault.key-vault.name}"
}
