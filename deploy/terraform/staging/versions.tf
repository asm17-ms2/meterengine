terraform {
  required_version = "= 1.17.1"
  backend "s3" {
    bucket       = "meterengine-tfstate-928618307647"
    key          = "deploy/staging/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.69.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "= 3.9.1"
    }
  }
}

provider "aws" {
  region              = "ap-northeast-2"
  allowed_account_ids = ["928618307647"]
}
