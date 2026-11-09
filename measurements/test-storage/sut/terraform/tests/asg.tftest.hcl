# 정책 문서는 실제 JSON이어야 역할과 정책의 plan 검증을 지난다.
mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
}

mock_provider "random" {}

# 인증서 검증 레코드의 for_each 키가 plan에서 정해져야 한다.
override_resource {
  target          = aws_acm_certificate.sut
  override_during = plan
  values = {
    arn = "arn:aws:acm:ap-northeast-2:928618307647:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [{
      domain_name           = "test-storage.meterengine.com"
      resource_record_name  = "_test.test-storage.meterengine.com."
      resource_record_type  = "CNAME"
      resource_record_value = "_test.acm-validations.aws."
    }]
  }
}

run "servers_boot_from_the_baked_image_with_the_shared_template" {
  command = plan

  assert {
    condition     = aws_launch_template.sut.image_id == var.ami_id
    error_message = "The launch template must use the baked app image in var.ami_id."
  }
  assert {
    condition = alltrue([
      strcontains(base64decode(aws_launch_template.sut.user_data), "./deploy/deploy.sh 'test-storage' \"$version\""),
      strcontains(base64decode(aws_launch_template.sut.user_data), "--name '/meterengine/test-storage/app-version'"),
    ])
    error_message = "The boot script must deploy test-storage from its own version parameter."
  }
  assert {
    condition     = one(aws_launch_template.sut.block_device_mappings[0].ebs).volume_size == 30
    error_message = "The root volume must fit the images the boot script pulls."
  }
}

run "replacement_keeps_a_healthy_server" {
  command = plan

  assert {
    condition = (
      aws_autoscaling_group.sut.instance_maintenance_policy[0].min_healthy_percentage == 100
      && aws_autoscaling_group.sut.instance_maintenance_policy[0].max_healthy_percentage == 200
      && aws_autoscaling_group.sut.health_check_grace_period == 600
    )
    error_message = "Health replacement must start a new server first and wait as long as staging and production."
  }
}
