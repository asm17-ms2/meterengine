# 두 측정 환경이 같이 쓰는 이미지 저장소. staging 저장소와 나눠 실험 이미지가 cd-staging 수동 실행으로 staging에 배포되지 않게 한다.
# 이름은 sut/module/locals.tf의 ecr_repository_names와 deploy/environments/test-sjpark-*.json의 접두와 같다.
locals {
  repository_names = ["meterengine-test-sjpark-backend", "meterengine-test-sjpark-frontend"]
}

resource "aws_ecr_repository" "image" {
  for_each             = toset(local.repository_names)
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
