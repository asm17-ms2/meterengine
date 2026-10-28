#!/bin/bash
# usage: push-stack.sh <층>:<feat 뒤 이름>:<옛 head>:<새 head> ...  (위층부터 적는다)
# origin head가 옛 head와 같고 새 head가 그 뒤에 이어질 때만 올린다. 하나라도 어긋나면 멈춘다.
set -uo pipefail
git fetch -q origin
for e in "$@"; do
  IFS=: read -r n b old new <<<"$e"
  cur=$(git ls-remote origin "refs/heads/feat/$b" | cut -f1)
  [ "$cur" = "$(git rev-parse "$old")" ] || { echo "STOP $n feat/$b가 ${cur:0:8}로 움직였다"; exit 1; }
  git merge-base --is-ancestor "$old" "$new" || { echo "STOP $n fast-forward가 아니다"; exit 1; }
  git push -q origin "$(git rev-parse "$new"):refs/heads/feat/$b" || { echo "STOP $n push 실패"; exit 1; }
  echo "$(date '+%T') pushed $n feat/$b ${old:0:8} -> ${new:0:8}"
done
