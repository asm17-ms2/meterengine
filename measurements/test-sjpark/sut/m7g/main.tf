module "sut" {
  source = "../module"

  environment       = "test-sjpark-m7g"
  instance_type     = "m7g.large"
  db_instance_class = "db.m7g.large"
  # 전환 1단계에서 만든 스냅샷 이름이 다르면 두 루트를 같이 고친다.
  snapshot_identifier = "meterengine-production-to-test-sjpark-261109"

  asg_min_size    = var.asg_min_size
  asg_max_size    = var.asg_max_size
  auto_stop_state = var.auto_stop_state
}
