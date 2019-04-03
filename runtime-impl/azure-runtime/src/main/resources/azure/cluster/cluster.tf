resource "azurerm_kubernetes_cluster" "k8s" {
  name       = "${var.aks_name}"
  location   = "${data.azurerm_resource_group.rg.location}"
  dns_prefix = "${var.aks_dns_prefix}"

  resource_group_name = "${data.azurerm_resource_group.rg.name}"

  addon_profile {
    http_application_routing {
      enabled = false
    }
  }

  agent_pool_profile {
    name            = "agentpool"
    count           = "${var.aks_agent_count}"
    vm_size         = "${var.aks_agent_vm_size}"
    os_type         = "Linux"
    os_disk_size_gb = "${var.aks_agent_os_disk_size}"
    vnet_subnet_id  = "${data.azurerm_subnet.kubesubnet.id}"
  }

  service_principal {
    client_id     = "${var.jemo_user_client_id}"
    client_secret = "${var.jemo_user_client_secret}"
  }

  network_profile {
    network_plugin     = "azure"
    dns_service_ip     = "${var.aks_dns_service_ip}"
    docker_bridge_cidr = "${var.aks_docker_bridge_cidr}"
    service_cidr       = "${var.aks_service_cidr}"
  }

  tags       = "${var.tags}"

  depends_on = ["azurerm_application_gateway.gateway"]
}