output "repository_names" {
  value = sort([for repository in aws_ecr_repository.image : repository.name])
}
