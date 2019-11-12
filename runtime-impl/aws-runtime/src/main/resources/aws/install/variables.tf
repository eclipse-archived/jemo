variable "terraform_user_access_key" {
  description = "The access key of the terraform user."
  type    = "string"
}

variable "terraform_user_secret_key" {
  description = "The secret key of the terraform user."
  type    = "string"
}

variable "region" {
  description = "The region you want to create the jemo user on."
  type    = "string"
}