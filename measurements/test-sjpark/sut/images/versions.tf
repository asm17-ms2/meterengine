terraform {
  required_version = "= 1.17.1"

  backend "s3" {
    bucket       = "meterengine-tfstate-928618307647"
    key          = "measurements/test-sjpark/images/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.69.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project     = "meterengine"
      Environment = "test-sjpark"
      Owner       = "sjparkx1129"
      ManagedBy   = "terraform"
    }
  }
}
