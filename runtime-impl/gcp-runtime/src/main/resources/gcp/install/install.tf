provider "google" {
  version      = "2.6"
  credentials  = "${file("${var.credentials_file}")}"
  project      = "${var.project_id}"
  region       = "${var.region}"
}

resource "google_service_account" "user" {
  account_id   = "jemo-user"
  display_name = "Jemo User"
  project      = "${var.project_id}"
}

resource "google_project_iam_custom_role" "jemo-role" {
  role_id     = "jemoRole"
  title       = "Jemo Role"
  description = "Permissions needed to run Jemo"
  permissions = ["iam.serviceAccounts.get", "resourcemanager.projects.getIamPolicy"]
  project      = "${var.project_id}"
}

resource "null_resource" "delay" {
  provisioner "local-exec" {
    command = "sleep 60"
  }
  triggers = {
    "before" = "${google_project_service.cloudresourcemanager.id}"
  }
}

resource "google_project_iam_binding" "jemo-role-binding" {
  project = "${var.project_id}"
  role    = "${google_project_iam_custom_role.jemo-role.id}"

  members  = [
    "serviceAccount:${google_service_account.user.email}"
  ]

  depends_on = ["null_resource.delay"]
}

resource "google_project_iam_binding" "datastore-role" {
  project = "${var.project_id}"
  role    = "roles/datastore.user"

  members  = [
    "serviceAccount:${google_service_account.user.email}"
  ]

  depends_on = ["null_resource.delay"]
}

resource "google_project_iam_binding" "storage-role" {
  project = "${var.project_id}"
  role    = "roles/storage.admin"

  members  = [
    "serviceAccount:${google_service_account.user.email}"
  ]

  depends_on = ["null_resource.delay"]
}

resource "google_project_iam_binding" "logging-role" {
  project = "${var.project_id}"
  role    = "roles/logging.admin"

  members  = [
    "serviceAccount:${google_service_account.user.email}"
  ]

  depends_on = ["null_resource.delay"]
}

resource "google_service_account_key" "key" {
  service_account_id = "${google_service_account.user.id}"
  public_key_type = "TYPE_X509_PEM_FILE"
}

resource "local_file" "myaccountjson" {
  content  = "${base64decode(google_service_account_key.key.private_key)}"
  filename = "${path.module}/jemo-user@${var.project_id}-cred.json"
}

resource "google_project_service" "cloudresourcemanager" {
  project = "${var.project_id}"
  service = "cloudresourcemanager.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "datastore" {
  project = "${var.project_id}"
  service = "datastore.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "storage" {
  project = "${var.project_id}"
  service = "storage-api.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "container" {
  project = "${var.project_id}"
  service = "container.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "logging" {
  project = "${var.project_id}"
  service = "logging.googleapis.com"
  disable_on_destroy = false
}

output "user_account_id" {
  value = "${google_service_account.user.account_id}"
}
