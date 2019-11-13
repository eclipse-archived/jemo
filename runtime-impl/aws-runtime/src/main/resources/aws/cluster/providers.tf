provider "aws" {
  version = "~> 2.7"
  access_key = "${var.terraform_user_access_key}"
  secret_key = "${var.terraform_user_secret_key}"
  region = "${var.region}"
}

data "aws_availability_zones" "available" {}

data "aws_caller_identity" "current" {}