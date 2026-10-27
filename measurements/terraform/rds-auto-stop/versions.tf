terraform {
  required_version = "~> 1.16"

  backend "s3" {
    bucket       = "meterengine-tfstate-928618307647"
    key          = "measurements/rds-auto-stop/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region              = "ap-northeast-2"
  allowed_account_ids = ["928618307647"]

  default_tags {
    tags = {
      Project = "meterengine"
      Owner   = "sjparkx1129"
      Purpose = "sut"
    }
  }
}
