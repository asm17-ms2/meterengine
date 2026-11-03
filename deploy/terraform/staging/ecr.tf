locals {
  ecr_repository_names = ["meterengine-staging-backend", "meterengine-staging-frontend"]
}

resource "aws_ecr_repository" "image" {
  for_each             = toset(local.ecr_repository_names)
  name                 = each.key
  image_tag_mutability = "IMMUTABLE"
}

resource "aws_ecr_lifecycle_policy" "image" {
  for_each   = aws_ecr_repository.image
  repository = each.value.name
  policy = jsonencode({ rules = [
    {
      rulePriority = 1
      description  = "태그 있는 이미지는 최근 10개만 남긴다"
      selection    = { tagStatus = "tagged", tagPatternList = ["*"], countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    },
    {
      rulePriority = 2
      description  = "참조되지 않는 매니페스트를 14일 후 정리"
      selection    = { tagStatus = "untagged", countType = "sinceImagePushed", countUnit = "days", countNumber = 14 }
      action       = { type = "expire" }
    }
  ] })
}
