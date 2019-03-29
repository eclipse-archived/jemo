variable "terraform_user_access_key" {
  description = "The access key of the terraform user."
  type    = "string"
}

variable "terraform_user_secret_key" {
  description = "The secret key of the terraform user."
  type    = "string"
}

variable "region" {
  default = "us-east-1"
}

variable "cluster-name" {
  default = "jemo-cluster"
}

variable "cluster-role-name" {
  default = "jemo-cluster-role"
}

variable "cluster-security-group-name" {
  default = "jemo-cluster-security-group"
}

variable "cluster-security-group-name-tag" {
  default = "jemo-cluster"
}

variable "workstation-external-cidr" {
  default = ""
}

variable "jemo-policy-name" {
  default = "jemo-policy"
}

variable "vpc-name-tag" {
  default = "jemo-vpc"
}

variable "subnet-name-tag" {
  default = "jemo-subnet"
}

variable "internet-gateway-name-tag" {
  default = "jemo-internet-gateway"
}

variable "worker-node-role-name" {
  default = "jemo-node-role"
}

variable "worker-node-instance-profile-name" {
  default = "jemo-node-instance-profile"
}

variable "worker-node-security-group-name" {
  default = "jemo-node-security-group"
}

variable "launch-conf-instance-type" {
  default = "m4.large"
}

variable "launch-conf-name-prefix" {
  default = "jemo"
}

variable "autoscaling-group-name" {
  default = "jemo"
}

variable "autoscaling-group-desired-capacity" {
  default = 2
}

variable "autoscaling-group-max-size" {
  default = 2
}

variable "autoscaling-group-min-size" {
  default = 1
}

variable "existing-vpc-id" {
  default = ""
}

