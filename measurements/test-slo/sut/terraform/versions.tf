terraform {
  required_version = "~> 1.16"

  # 상태를 S3에 둬 어느 컴퓨터에서든 같은 상태로 plan, apply한다. 잠금은 S3 잠금 파일로 해 DynamoDB가 필요 없다.
  backend "s3" {
    bucket       = "meterengine-tfstate-928618307647"
    key          = "measurements/test-slo/sut/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project = "meterengine"
      Owner   = var.owner
      Purpose = "sut"
    }
  }
}
