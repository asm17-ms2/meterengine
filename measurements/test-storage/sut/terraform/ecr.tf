# 이 환경만 쓰는 이미지 저장소. 머지 전 브랜치의 이미지를 올려 잰다.
# staging 저장소에 올리면 그 이미지가 cd-staging 수동 실행으로 staging에 배포될 수 있어 나눈다.
# 이름은 deploy/environments/test-storage.json의 image_repository_prefix와 같아야 서버가 받는다.
resource "aws_ecr_repository" "image" {
  for_each             = toset(local.ecr_repos)
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
