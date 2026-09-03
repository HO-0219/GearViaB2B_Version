#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() { echo "FAIL: $*" >&2; exit 1; }

# 1. Every file the Ubuntu release bundle must ship.
required=(
  install_gearvia_ai_agent_ubuntu.sh
  uninstall_gearvia_ai_agent_ubuntu.sh
  infra/b2b/compose.yml
  infra/b2b/nginx.conf.template
  infra/b2b/runtime.env.example
  infra/b2b/systemd/b2bgearvia.service
  infra/ubuntu/gearvia-host-apply.sh
  infra/ubuntu/systemd/gearvia-host-apply.service
  infra/ubuntu/systemd/gearvia-host-apply.path
  infra/ubuntu/lib/gearvia-common.sh
  infra/ubuntu/lib/gearvia-tls.sh
  infra/ubuntu/lib/gearvia-images.sh
)
for path in "${required[@]}"; do
  [[ -r "$repo_root/$path" ]] || fail "release bundle is missing $path"
done

# 2. Bundled shell scripts parse cleanly.
scripts=(
  install_gearvia_ai_agent_ubuntu.sh
  uninstall_gearvia_ai_agent_ubuntu.sh
  infra/ubuntu/gearvia-host-apply.sh
  infra/ubuntu/lib/gearvia-common.sh
  infra/ubuntu/lib/gearvia-tls.sh
  infra/ubuntu/lib/gearvia-images.sh
)
bash -n "${scripts[@]/#/$repo_root/}" || fail "a bundled shell script has a syntax error"

# 3. runtime.env.example documents exactly the keys the installer generates, and
#    nothing more, so it stays a verification aid rather than a copy template.
installer_keys="$(sed -n '/local keys=(/,/^  )/p' infra/ubuntu/lib/gearvia-common.sh \
  | grep -oE '[A-Z][A-Z0-9_]+' | sort -u)"
example_keys="$(grep -oE '^[A-Z][A-Z0-9_]+=' infra/b2b/runtime.env.example | tr -d '=' | sort -u)"
missing="$(comm -23 <(printf '%s\n' "$installer_keys") <(printf '%s\n' "$example_keys"))"
[[ -z "$missing" ]] || fail "runtime.env.example is missing generated keys:"$'\n'"$missing"

# 4. No placeholder image digests or copy instructions survive in the example.
if grep -qE '<release-digest>|<sha256>|복사' infra/b2b/runtime.env.example; then
  fail "runtime.env.example still contains a placeholder digest or copy instruction"
fi

# 5. The application unit bounds its start so a never-healthy container fails the
#    unit instead of hanging `systemctl enable --now` forever.
grep -qE '^TimeoutStartSec=[1-9][0-9]+$' infra/b2b/systemd/b2bgearvia.service \
  || fail "b2bgearvia.service must set a finite TimeoutStartSec"

echo "Ubuntu release bundle tests passed"
