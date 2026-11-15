output "bootstrap_brokers_tls" {
  description = "TLS(9094)로 붙는 브로커 주소"
  value       = aws_msk_cluster.events.bootstrap_brokers_tls
}

output "cluster_arn" {
  value = aws_msk_cluster.events.arn
}

output "security_group_id" {
  value = aws_security_group.kafka.id
}

# 아래는 루트의 시험이 설정을 확인하는 데 쓴다.
output "broker_count" {
  value = aws_msk_cluster.events.number_of_broker_nodes
}

output "broker_instance_type" {
  value = aws_msk_cluster.events.broker_node_group_info[0].instance_type
}

output "client_broker_encryption" {
  value = aws_msk_cluster.events.encryption_info[0].encryption_in_transit[0].client_broker
}

output "server_properties" {
  value = aws_msk_configuration.events.server_properties
}

output "client_ports" {
  value = {
    ingress = aws_vpc_security_group_ingress_rule.kafka_from_clients.from_port
    egress  = aws_vpc_security_group_egress_rule.clients_to_kafka.from_port
  }
}
