mock_provider "aws" {}

run "repositories_match_the_environment_prefix" {
  command = plan

  assert {
    condition     = toset(keys(aws_ecr_repository.image)) == toset(["meterengine-test-sjpark-backend", "meterengine-test-sjpark-frontend"])
    error_message = "Repository names must be the measurement prefix plus backend and frontend."
  }
  assert {
    condition     = alltrue([for repository in aws_ecr_repository.image : repository.image_tag_mutability == "IMMUTABLE"])
    error_message = "Tags must be immutable so a commit SHA always means one image."
  }
}
