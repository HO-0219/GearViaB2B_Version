#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=infra/ubuntu/lib/gearvia-common.sh
. "$repo_root/infra/ubuntu/lib/gearvia-common.sh"
# shellcheck source=infra/ubuntu/lib/gearvia-images.sh
. "$repo_root/infra/ubuntu/lib/gearvia-images.sh"

tmp_root="$(mktemp -d)"
trap 'rm -rf -- "$tmp_root"' EXIT

log="$tmp_root/docker.log"
fake_docker="$tmp_root/docker"
# Fake docker: records argv; "image inspect" fails unless the tag is listed in
# $tmp_root/present so tests can force build/pull or reuse paths on demand.
cat > "$fake_docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_LOG"
present() { grep -Fqx "$1" "$DOCKER_PRESENT" 2>/dev/null; }
mark() { present "$1" || printf '%s\n' "$1" >> "$DOCKER_PRESENT"; }
case "$1 $2" in
  "image inspect")
    tag="${!#}"
    present "$tag" || exit 1
    [[ "$3" == "--format" ]] && printf 'sha256:deadbeef-%s\n' "$tag"
    exit 0 ;;
  "build "*)
    for ((i = 1; i <= $#; i++)); do [[ "${!i}" == "--tag" || "${!i}" == "-t" ]] && { j=$((i + 1)); mark "${!j}"; }; done
    exit 0 ;;
  "pull "*) mark "$2"; exit 0 ;;
  "load "*) exit 0 ;;
esac
exit 0
EOF
chmod +x "$fake_docker"
export DOCKER_LOG="$log" DOCKER_PRESENT="$tmp_root/present"
: > "$DOCKER_PRESENT"
export GEARVIA_DOCKER_BIN="$fake_docker"
export GEARVIA_REPO_ROOT="$tmp_root"
mkdir -p "$tmp_root/infra/images" "$tmp_root/backend"
: > "$tmp_root/infra/images/backend.tar"
: > "$tmp_root/backend/Dockerfile"

fail() { echo "FAIL: $*" >&2; exit 1; }
line_of() { grep -nF -- "$1" "$log" | head -n1 | cut -d: -f1; }
assert_log_order() {
  local prev=0 cur label
  for label in "$@"; do
    cur="$(line_of "$label")"
    [[ -n "$cur" ]] || fail "expected docker call not logged: $label"
    (( cur > prev )) || fail "docker call out of order: $label"
    prev="$cur"
  done
}

# 1. Bundle load, then inspect miss, then source build (in that order).
gearvia_prepare_image backend b2bgearvia-backend:test backend/Dockerfile >/dev/null
grep -q 'load --input .*infra/images/backend.tar' "$log" || fail "release bundle was not loaded"
assert_log_order 'load --input' 'image inspect b2bgearvia-backend:test' 'build -f backend/Dockerfile'

# 2. Local reuse: inspect succeeds, no bundle, no build/pull.
: > "$log"
printf '%s\n' 'b2bgearvia-web:test' > "$DOCKER_PRESENT"
gearvia_prepare_image web b2bgearvia-web:test frontend/Dockerfile >/dev/null
if grep -q 'build ' "$log"; then fail "build ran while a local image was reusable"; fi
if grep -q 'pull ' "$log"; then fail "pull ran while a local image was reusable"; fi

# 3. No Dockerfile: inspect miss falls back to pull.
: > "$log"
: > "$DOCKER_PRESENT"
gearvia_prepare_image mysql mysql:8.4 '' >/dev/null
assert_log_order 'image inspect mysql:8.4' 'pull mysql:8.4'
if grep -q 'build ' "$log"; then fail "build ran for a registry image without a Dockerfile"; fi

# 4. Image state records a ref and id for every prepared image.
: > "$log"
printf '%s\n' 'b2bgearvia-backend:test' 'b2bgearvia-web:test' 'mysql:8.4' 'busybox:1.37' > "$DOCKER_PRESENT"
export GEARVIA_BACKEND_IMAGE='b2bgearvia-backend:test' GEARVIA_WEB_IMAGE='b2bgearvia-web:test'
export GEARVIA_MYSQL_IMAGE='mysql:8.4' GEARVIA_INIT_DATA_IMAGE='busybox:1.37'
state_file="$tmp_root/install-state.env"
: > "$state_file"
gearvia_record_image_state "$state_file"
for key in BACKEND_IMAGE_ID WEB_IMAGE_ID MYSQL_IMAGE_ID INIT_DATA_IMAGE_ID \
           BACKEND_IMAGE_REF WEB_IMAGE_REF MYSQL_IMAGE_REF INIT_DATA_IMAGE_REF; do
  grep -Eq "^${key}=.+" "$state_file" || fail "install state missing $key"
done

# 5. Missing image aborts state recording instead of writing a blank id.
: > "$DOCKER_PRESENT"
if ( gearvia_record_image_state "$tmp_root/broken-state.env" ) >/dev/null 2>&1; then
  fail "state recording accepted an unavailable image"
fi

echo "Ubuntu image selection tests passed"
