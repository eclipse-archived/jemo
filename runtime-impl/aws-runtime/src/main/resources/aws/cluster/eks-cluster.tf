#
# EKS Cluster Resources
#  * IAM Role to allow EKS service to manage other AWS services
#  * EC2 Security Group to allow networking traffic with EKS cluster
#  * EKS Cluster
#

resource "aws_iam_role" "jemo-cluster" {
  name = "${var.cluster-role-name}"

  assume_role_policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "eks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
POLICY
}

resource "aws_iam_role_policy_attachment" "jemo-cluster-AmazonEKSClusterPolicy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = "${aws_iam_role.jemo-cluster.name}"
}

resource "aws_iam_role_policy_attachment" "jemo-cluster-AmazonEKSServicePolicy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSServicePolicy"
  role       = "${aws_iam_role.jemo-cluster.name}"
}

resource "aws_iam_role_policy_attachment" "jemo-policy-attachment" {
  policy_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.jemo-policy-name}"
  role       = "${aws_iam_role.jemo-cluster.name}"
}

resource "aws_security_group" "jemo-cluster" {
  name        = "${var.cluster-security-group-name}"
  description = "Cluster communication with worker nodes"
  vpc_id      = "${_JEMO_VPC_ID_}"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags {
    Name = "${var.cluster-security-group-name-tag}"
  }
}

resource "aws_security_group_rule" "jemo-cluster-ingress-node-https" {
  description              = "Allow pods to communicate with the cluster API Server"
  from_port                = 443
  protocol                 = "tcp"
  security_group_id        = "${aws_security_group.jemo-cluster.id}"
  source_security_group_id = "${aws_security_group.jemo-node.id}"
  to_port                  = 443
  type                     = "ingress"
}

resource "aws_security_group_rule" "jemo-cluster-ingress-workstation-https" {
  cidr_blocks       = ["${var.workstation-external-cidr}"]
  description       = "Allow workstation to communicate with the cluster API Server"
  from_port         = 443
  protocol          = "tcp"
  security_group_id = "${aws_security_group.jemo-cluster.id}"
  to_port           = 443
  type              = "ingress"
}

resource "aws_eks_cluster" "jemo" {
  name     = "${var.cluster-name}"
  role_arn = "${aws_iam_role.jemo-cluster.arn}"

  vpc_config {
    security_group_ids = ["${aws_security_group.jemo-cluster.id}"]
    subnet_ids         = ["${_JEMO_SUBNET_IDS_}"]
  }

  depends_on = [
    "aws_iam_role_policy_attachment.jemo-cluster-AmazonEKSClusterPolicy",
    "aws_iam_role_policy_attachment.jemo-cluster-AmazonEKSServicePolicy",
  ]
}
