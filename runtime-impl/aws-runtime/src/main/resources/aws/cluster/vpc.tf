#
# VPC Resources
#  * VPC
#  * Subnets
#  * Internet Gateway
#  * Route Table
#

resource "aws_vpc" "jemo" {
  cidr_block = "10.0.0.0/16"

  tags = "${
    map(
     "Name", "${var.vpc-name-tag}",
     "kubernetes.io/cluster/${var.cluster-name}", "shared",
    )
  }"
}

resource "aws_subnet" "jemo" {
  count = "2"

  availability_zone = "${data.aws_availability_zones.available.names[count.index]}"
  cidr_block        = "10.0.${count.index}.0/24"
  vpc_id            = "${aws_vpc.jemo.id}"

  tags = "${
    map(
     "Name", "${var.subnet-name-tag}",
     "kubernetes.io/cluster/${var.cluster-name}", "shared",
    )
  }"
}

resource "aws_internet_gateway" "jemo" {
  vpc_id = "${aws_vpc.jemo.id}"

  tags {
    Name = "${var.internet-gateway-name-tag}"
  }
}

resource "aws_route_table" "jemo" {
  vpc_id = "${aws_vpc.jemo.id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.jemo.id}"
  }
}

resource "aws_route_table_association" "jemo" {
  count = "2"

  subnet_id      = "${aws_subnet.jemo.*.id[count.index]}"
  route_table_id = "${aws_route_table.jemo.id}"
}
