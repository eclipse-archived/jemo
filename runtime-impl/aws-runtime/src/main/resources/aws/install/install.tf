provider "aws" {
  version = "~> 2.7"
  access_key = "${var.terraform_user_access_key}"
  secret_key = "${var.terraform_user_secret_key}"
  region = "${var.region}"
}

resource "aws_iam_user" "user" {
  name = "jemo-user"
}

resource "aws_iam_access_key" "access_key" {
  user = "${aws_iam_user.user.name}"
}

resource "aws_iam_group" "group" {
  name = "jemo-group"
  path = "/jemo_group/"
}

resource "aws_iam_group_membership" "membership" {
  name = "jemo_users_group_membership"

  users = [
    "${aws_iam_user.user.name}"
  ]

  group = "${aws_iam_group.group.name}"
}

resource "aws_iam_policy" "policy" {
  name        = "jemo-policy"
  description = "A policy with permissions required by Jemo"
  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "dynamodb:BatchWriteItem",
        "dynamodb:CreateTable",
        "dynamodb:DeleteTable",
        "dynamodb:ListTables",
        "dynamodb:DescribeTable",
        "dynamodb:Scan",
        "dynamodb:Query",
        "dynamodb:GetItem",
        "s3:AbortMultipartUpload",
        "s3:CreateBucket",
        "s3:DeleteObject",
        "s3:GetObject",
        "s3:ListBucketMultipartUploads",
        "s3:ListBucket",
        "s3:PutObject",
        "sqs:CreateQueue",
        "sqs:DeleteMessage",
        "sqs:DeleteQueue",
        "sqs:GetQueueUrl",
        "sqs:ListQueues",
        "sqs:SendMessage",
        "sqs:SetQueueAttributes",
        "sqs:ReceiveMessage",
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:DescribeLogStreams",
        "logs:PutLogEvents",
        "iam:GetUser",
        "iam:SimulatePrincipalPolicy",
        "iam:GetRole",
        "iam:ListPolicies",
        "ec2:DescribeTags",
        "ec2:DescribeVpcs"
      ],
      "Effect": "Allow",
      "Resource": "*"
    }
  ]
}
EOF
}

resource "aws_iam_group_policy_attachment" "test-attach" {
  group      = "${aws_iam_group.group.name}"
  policy_arn = "${aws_iam_policy.policy.arn}"
}

output "jemo_user_access_key_id" {
  value = "${aws_iam_access_key.access_key.id}"
}

output "jemo_user_secret_access_key" {
  value = "${aws_iam_access_key.access_key.secret}"
}

