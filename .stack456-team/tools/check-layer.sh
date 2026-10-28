#!/bin/bash
# usage: check-layer.sh <층 worktree>  (그 층 head에서 프론트, 계약 명세, 배포 시험, 백엔드 빌드를 차례로 돌린다)
set -uo pipefail
d=$(cd "$1" && pwd); log=${LOG_DIR:-$d/.s456-check}; mkdir -p "$log"
fail=0
step() { local name=$1 dir=$2; shift 2; ( cd "$dir" && "$@" ) > "$log/$name.log" 2>&1; local rc=$?; echo "$name rc=$rc"; [ $rc = 0 ] || fail=1; }
[ -z "$(git -C "$d" status --porcelain -- . ':!.s456-check')" ] || { echo "STOP: $d 작업 트리가 깨끗하지 않다"; exit 2; }
echo "head $(git -C "$d" rev-parse --short=8 HEAD)"
step install "$d/frontend" corepack pnpm install --frozen-lockfile --prefer-offline
step typecheck "$d/frontend" corepack pnpm typecheck
step lint "$d/frontend" corepack pnpm lint
step unit "$d/frontend" node --test "e2e/*.test.mjs"
step build "$d/frontend" corepack pnpm build
step contract "$d/frontend" corepack pnpm test:e2e:contract
[ -f "$d/frontend/playwright.payment.config.ts" ] && step payment "$d/frontend" corepack pnpm exec playwright test -c playwright.payment.config.ts
step deploy "$d" python3 -m unittest discover -s deploy/tests -p 'test_*.py'
step gradle "$d/backend" ./gradlew build --continue
st=$(git -C "$d" status --porcelain -- . ':!.s456-check')
echo "git status: ${st:-clean}"; [ -z "$st" ] || fail=1
echo "overall fail=$fail (로그 $log)"
exit $fail
