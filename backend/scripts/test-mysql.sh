#!/usr/bin/env bash
set -euo pipefail

backend_dir="$(cd "$(dirname "$0")/.." && pwd)"
project_dir="$(cd "$backend_dir/.." && pwd)"
env_file="$project_dir/.env"

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

test_database="teamProject_test"
test_url="$(printf '%s\n' "$db_url" | sed -E "s#(jdbc:mysql://[^/]+/)[^?]+#\\1${test_database}#")"

if [[ "$test_url" == "$db_url" || "$test_url" != *"/${test_database}"* ]]; then
  echo "테스트 DB URL을 안전하게 만들지 못했습니다." >&2
  exit 1
fi

MYSQL_PWD="$db_password" mysql --protocol=TCP -h localhost -P 3306 -u "$db_user" \
  -e "CREATE DATABASE IF NOT EXISTS ${test_database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

cd "$backend_dir"
./mvnw -q test \
  -Dspring.datasource.url="$test_url" \
  -Dspring.datasource.username="$db_user" \
  -Dspring.datasource.password="$db_password" \
  -Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
