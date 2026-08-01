#!/usr/bin/env bash
#
# docs/erd/generated 를 다시 만든다.
#
#   ./scripts/generate-erd.sh
#
# 일회용 postgres 컨테이너를 띄워 마이그레이션을 적용하고 tbls로 문서를 뽑은 뒤 버린다.
# 개발용 DB(docker compose의 meterengine)는 건드리지 않는다. docker만 있으면 되고
# tbls를 로컬에 설치할 필요는 없다.
#
# 마이그레이션 적용에 Flyway 대신 psql을 쓴다. 파일명 순서대로 적용하는 것은 빈 DB에
# Flyway를 돌리는 것과 같고, Flyway가 이 SQL을 실제로 파싱하는지는 backend 테스트
# (SchemaConstraintTest, Testcontainers + Spring Boot)가 이미 검증한다.
# 여기서 Flyway 이미지(962MB)를 한 번 더 받을 이유가 없다.
#
# 스키마를 바꿨으면 이 스크립트를 돌리고 생성물을 마이그레이션과 같은 커밋에 넣는다.
# 빠뜨리면 CI의 erd job이 실패한다 (docs/erd/README.md 참조).

set -euo pipefail

# .tbls.yml의 requiredVersion과 함께 맞춘다. 둘 중 하나만 올리면 tbls가 막는다.
TBLS_IMAGE="ghcr.io/k1low/tbls:v1.95.0"
POSTGRES_IMAGE="postgres:18"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/backend/src/main/resources/db/migration"

DB_NAME="erd"
DB_USER="erd"
DB_PASSWORD="erd"

# 같은 머신에서 두 번 돌려도 부딪히지 않게 한다.
SUFFIX="$$"
CONTAINER="meterengine-erd-$SUFFIX"
NETWORK="meterengine-erd-net-$SUFFIX"

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> 일회용 postgres 기동"
docker network create "$NETWORK" >/dev/null
docker run -d --name "$CONTAINER" --network "$NETWORK" \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  "$POSTGRES_IMAGE" >/dev/null

# -h 127.0.0.1로 TCP를 확인한다. postgres 이미지는 초기화 중에 유닉스 소켓 전용
# 임시 서버를 띄우므로, 소켓으로 검사하면 아직 준비되지 않은 서버를 준비됐다고 읽는다.
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" pg_isready -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! docker exec "$CONTAINER" pg_isready -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
  echo "postgres가 60초 안에 뜨지 않았다" >&2
  exit 1
fi

echo "==> 마이그레이션 적용"
# V<숫자>__ 의 숫자 순서로 적용한다 (V2가 V10보다 먼저 오도록 사전순이 아닌 버전순).
find "$MIGRATIONS" -name 'V*.sql' -print0 \
  | sort -z -t_ -k1.2 -V \
  | while IFS= read -r -d '' file; do
      echo "    $(basename "$file")"
      docker exec -i "$CONTAINER" \
        psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q < "$file"
    done

echo "==> tbls 문서 생성"
docker run --rm --network "$NETWORK" \
  --user "$(id -u):$(id -g)" \
  -v "$ROOT:/work" -w /work \
  -e TBLS_DB_HOST="$CONTAINER" \
  -e TBLS_DB_PORT=5432 \
  -e TBLS_DB_NAME="$DB_NAME" \
  -e TBLS_DB_USER="$DB_USER" \
  -e TBLS_DB_PASSWORD="$DB_PASSWORD" \
  "$TBLS_IMAGE" doc --force --rm-dist

echo "==> 완료: docs/erd/generated"
