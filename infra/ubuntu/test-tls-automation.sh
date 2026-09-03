#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$repo_root/infra/ubuntu/lib/gearvia-common.sh"
# shellcheck source=infra/ubuntu/lib/gearvia-tls.sh
. "$repo_root/infra/ubuntu/lib/gearvia-tls.sh"

tmp_root="$(mktemp -d)"
trap 'rm -rf -- "$tmp_root"' EXIT

selected="$(GEARVIA_TEST_IP_CANDIDATES='203.0.113.10 10.20.30.40 192.168.10.20' gearvia_detect_primary_address)"
[[ "$selected" == "10.20.30.40" ]] || { echo "FAIL: private primary address was not selected" >&2; exit 1; }

# The VirtualBox NAT address (10.0.2.15) is listed first because it owns the
# default route, but the host-only address is the one the instance is reached on.
selected="$(GEARVIA_TEST_IP_CANDIDATES='10.0.2.15 10.0.2.15/24 192.168.56.102/24 172.17.0.1/16' gearvia_detect_primary_address)"
[[ "$selected" == "192.168.56.102" ]] || { echo "FAIL: NAT address won over the host-only address" >&2; exit 1; }

# NAT is still accepted when it is the only candidate.
selected="$(GEARVIA_TEST_IP_CANDIDATES='10.0.2.15/24' gearvia_detect_primary_address)"
[[ "$selected" == "10.0.2.15" ]] || { echo "FAIL: sole NAT address was not accepted as a fallback" >&2; exit 1; }

# An explicit override wins outright.
selected="$(GEARVIA_PUBLIC_ADDRESS='192.168.7.7' GEARVIA_TEST_IP_CANDIDATES='10.0.2.15' gearvia_detect_primary_address)"
[[ "$selected" == "192.168.7.7" ]] || { echo "FAIL: GEARVIA_PUBLIC_ADDRESS override was ignored" >&2; exit 1; }

if (GEARVIA_TEST_IP_CANDIDATES='' gearvia_detect_primary_address) 2>"$tmp_root/address.error"; then
  echo "FAIL: missing private address was accepted" >&2
  exit 1
fi
grep -Fq '사설 IPv4 주소를 감지하지 못했습니다' "$tmp_root/address.error"

gearvia_issue_server_cert "$tmp_root/tls" "10.20.30.40" "gearvia-node"
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'IP Address:10.20.30.40'
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'DNS:gearvia-node'
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'DNS:localhost'
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'IP Address:127.0.0.1'
openssl pkey -in "$tmp_root/tls/privkey.pem" -pubout -outform pem 2>/dev/null | sha256sum > "$tmp_root/key.before"
gearvia_issue_server_cert "$tmp_root/tls" "10.20.30.40" "gearvia-node"
openssl pkey -in "$tmp_root/tls/privkey.pem" -pubout -outform pem 2>/dev/null | sha256sum | diff - "$tmp_root/key.before"

# Re-issuing for a different address must actually replace the certificate:
# "openssl x509 -checkip" reports the mismatch only on stdout and still exits 0,
# so a check that trusts the exit code would keep the stale certificate.
gearvia_issue_server_cert "$tmp_root/tls" "192.168.44.55" "gearvia-node"
openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -F 'IP Address:192.168.44.55'
if openssl x509 -in "$tmp_root/tls/fullchain.pem" -noout -ext subjectAltName | grep -qF 'IP Address:10.20.30.40'; then
  echo "FAIL: stale certificate address was not replaced" >&2
  exit 1
fi

if [[ "$(uname -s)" != MINGW* ]]; then
  [[ "$(stat -c '%a' "$tmp_root/tls/ca.key")" == "600" ]]
  [[ "$(stat -c '%a' "$tmp_root/tls/privkey.pem")" == "600" ]]
  [[ "$(stat -c '%a' "$tmp_root/tls/ca.crt")" == "644" ]]
  [[ "$(stat -c '%a' "$tmp_root/tls/fullchain.pem")" == "644" ]]
fi

echo "Ubuntu TLS automation tests passed"
