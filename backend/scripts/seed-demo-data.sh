#!/usr/bin/env bash
set -euo pipefail

backend_dir="$(cd "$(dirname "$0")/.." && pwd)"
project_dir="$(cd "$backend_dir/.." && pwd)"
env_file="$project_dir/.env"
seed_file="$backend_dir/scripts/sql/demo-data.sql"

if [[ ! -f "$env_file" ]]; then
  echo "루트 .env 파일이 필요합니다." >&2
  exit 1
fi

db_url="$(sed -n 's/^SPRING_DATASOURCE_URL=//p' "$env_file")"
db_user="$(sed -n 's/^SPRING_DATASOURCE_USERNAME=//p' "$env_file")"
db_password="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$env_file")"

if [[ -z "$db_url" || -z "$db_user" ]]; then
  echo ".env의 MySQL 접속 정보를 확인해 주세요." >&2
  exit 1
fi

if [[ ! "$db_url" =~ ^jdbc:mysql://(localhost|127\.0\.0\.1)(:([0-9]+))?/teamProject([?].*)?$ ]]; then
  echo "안전상 localhost의 teamProject 개발 DB에서만 시연 데이터를 만들 수 있습니다." >&2
  exit 1
fi

db_host="${BASH_REMATCH[1]}"
db_port="${BASH_REMATCH[3]:-3306}"

MYSQL_PWD="$db_password" mysql --protocol=TCP -h "$db_host" -P "$db_port" -u "$db_user" \
  --default-character-set=utf8mb4 teamProject < "$seed_file"

echo "시연 데이터 준비가 완료되었습니다. 계정과 진행 순서는 docs/qa/DemoScenario.md를 확인하세요."
