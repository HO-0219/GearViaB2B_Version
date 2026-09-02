#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() { echo "FAIL: $*" >&2; exit 1; }

# Every shell script the on-prem installer sources or runs must stay LF: the
# Ubuntu target's bash does not strip a trailing CR, so a CRLF line breaks
# "set -euo pipefail", "[[ ... ]]" tests, and heredocs.
scripts=(
  install_gearvia_ai_agent_ubuntu.sh
  uninstall_gearvia_ai_agent_ubuntu.sh
  infra/ubuntu/lib/gearvia-common.sh
  infra/ubuntu/lib/gearvia-tls.sh
  infra/ubuntu/lib/gearvia-images.sh
  infra/ubuntu/gearvia-host-apply.sh
  infra/ubuntu/test-lifecycle-scripts.sh
  infra/ubuntu/test-tls-automation.sh
  infra/ubuntu/test-image-selection.sh
  infra/ubuntu/test-host-apply.sh
  infra/ubuntu/test-line-endings.sh
)

for script in "${scripts[@]}"; do
  [[ -f "$script" ]] || fail "missing script: $script"
  # Committed blob must be LF.
  if git show "HEAD:$script" 2>/dev/null | grep -qU $'\r'; then
    fail "committed blob has CRLF: $script"
  fi
  # .gitattributes must pin eol=lf so no dev's core.autocrlf can reintroduce CR.
  eol="$(git check-attr eol -- "$script" | sed 's/.*: //')"
  [[ "$eol" == "lf" ]] || fail "eol not pinned to lf for $script (got: $eol)"
done

echo "Ubuntu line-ending tests passed"
