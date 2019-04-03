# User Assigned Idntities
resource "azurerm_user_assigned_identity" "jemo-identity" {
  resource_group_name = "${data.azurerm_resource_group.rg.name}"
  location            = "${data.azurerm_resource_group.rg.location}"

  name = "jemo-identity"

  tags = "${var.tags}"
}
