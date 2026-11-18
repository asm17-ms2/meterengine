# 홈 인사이트를 하루에 한 번 계산해 두는 버킷. 그 환경의 앱 서버만 읽고 쓴다.
# 이름은 계정 전체에서 하나여야 해 계정 번호를 붙인다.
# 이름과 ARN을 값으로 먼저 정해 둔다. 버킷 자원의 속성을 참조하면 역할 정책이 apply 전에는 정해지지 않아 plan에서 내용을 볼 수 없다.
locals {
  insights_bucket_name = "meterengine-${var.environment}-insights-${data.aws_caller_identity.current.account_id}"
  insights_bucket_arn  = "arn:aws:s3:::${local.insights_bucket_name}"
}

resource "aws_s3_bucket" "insights" {
  bucket = local.insights_bucket_name
  tags   = local.tags
}

# 주소를 알아도 인터넷에서 열리지 않게 공개 경로를 모두 막는다.
resource "aws_s3_bucket_public_access_block" "insights" {
  bucket                  = aws_s3_bucket.insights.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 객체마다 권한을 따로 줄 수 없게 ACL을 끈다. 접근은 IAM 정책으로만 정한다.
resource "aws_s3_bucket_ownership_controls" "insights" {
  bucket = aws_s3_bucket.insights.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "insights" {
  bucket = aws_s3_bucket.insights.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 암호화하지 않은 연결로 온 요청은 누구의 것이든 거절한다.
resource "aws_s3_bucket_policy" "insights" {
  bucket = aws_s3_bucket.insights.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Sid       = "DenyInsecureTransport"
    Effect    = "Deny"
    Principal = "*"
    Action    = "s3:*"
    Resource  = [local.insights_bucket_arn, "${local.insights_bucket_arn}/*"]
    Condition = { Bool = { "aws:SecureTransport" = "false" } }
  }] })
  # 공개 차단이 먼저 걸려 있어야 정책을 넣는 동안에도 공개 정책이 들어갈 틈이 없다.
  depends_on = [aws_s3_bucket_public_access_block.insights]
}
