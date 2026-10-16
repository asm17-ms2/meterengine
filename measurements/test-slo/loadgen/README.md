# loadgen

test-slo SUT에 k6로 요청을 보내는 부하 발생기 EC2다. 돌리는 법은 [`../k6/README.md`](../k6/README.md).

- 접속은 SSM으로만 한다.
- 매일 04:00(서울)에 자동으로 꺼진다.
- 명령은 레포 루트에서 실행한다. Terraform 상태는 S3 `meterengine-tfstate-928618307647`에 있어 어느 컴퓨터에서든 같다.
- 필요한 것: Terraform 1.16, AWS CLI, `session-manager-plugin`, `ap-northeast-2`에 자원을 만들 수 있는 AWS 자격.

## 만들기와 지우기

```bash
terraform -chdir=measurements/test-slo/loadgen/terraform init
terraform -chdir=measurements/test-slo/loadgen/terraform plan -out=loadgen.tfplan
terraform -chdir=measurements/test-slo/loadgen/terraform apply loadgen.tfplan
terraform -chdir=measurements/test-slo/loadgen/terraform destroy
```

- 부팅하면서 k6와 git을 설치한다. 접속해서 `cloud-init status`가 `done`이고 `k6 version`이 나오면 끝난 것이다.

## 켜고 끄기

```bash
LOADGEN=$(terraform -chdir=measurements/test-slo/loadgen/terraform output -raw instance_id)
aws ec2 start-instances --instance-ids $LOADGEN
aws ec2 stop-instances --instance-ids $LOADGEN
```

- 04:00를 넘기는 측정을 하는 날에는 `-var auto_stop_state=DISABLED`로 apply하고, 끝나면 그 옵션 없이 다시 apply한다.
