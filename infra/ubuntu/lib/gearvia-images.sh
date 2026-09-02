#!/usr/bin/env bash

# Docker image preparation for the on-prem Ubuntu installer.
#
# Preparation priority for every image:
#   1. Load a release bundle at infra/images/<logical-name>.tar when present.
#   2. Reuse a matching local image when "docker image inspect" succeeds.
#   3. Build from source when a Dockerfile is given, otherwise pull the tag.

gearvia_docker() {
  "${GEARVIA_DOCKER_BIN:-docker}" "$@"
}

gearvia_image_bundle_dir() {
  printf '%s' "${GEARVIA_IMAGES_DIR:-${GEARVIA_REPO_ROOT:-.}/infra/images}"
}

# gearvia_prepare_image <logical-name> <tag> <dockerfile-or-empty>
gearvia_prepare_image() {
  local name="$1" tag="$2" dockerfile="${3:-}"
  [[ -n "$name" && -n "$tag" ]] || gearvia_die "gearvia_prepare_image requires a logical name and tag"
  local repo_root="${GEARVIA_REPO_ROOT:-.}" bundle id
  bundle="$(gearvia_image_bundle_dir)/$name.tar"

  if [[ -f "$bundle" ]]; then
    gearvia_docker load --input "$bundle" >/dev/null \
      || gearvia_die "Failed to load image bundle: $bundle"
  fi

  if ! gearvia_docker image inspect "$tag" >/dev/null 2>&1; then
    if [[ -n "$dockerfile" ]]; then
      [[ -f "$repo_root/$dockerfile" ]] || gearvia_die "Dockerfile not found: $dockerfile"
      # Leave build/pull diagnostics on stderr so a failure is actionable.
      ( cd "$repo_root" && gearvia_docker build -f "$dockerfile" --tag "$tag" . >/dev/null ) \
        || gearvia_die "Failed to build image $tag from $dockerfile"
    else
      gearvia_docker pull "$tag" >/dev/null \
        || gearvia_die "Failed to pull image: $tag"
    fi
  fi

  id="$(gearvia_docker image inspect --format '{{.Id}}' "$tag" 2>/dev/null || true)"
  [[ -n "$id" ]] || gearvia_die "Prepared image is still unavailable: $tag"
  printf '%s\n' "$id"
}

# gearvia_record_image_state <state-file>
# Appends the resolved image IDs for every prepared image.
gearvia_record_image_state() {
  local state_file="$1" pair name tag id
  [[ -n "$state_file" ]] || gearvia_die "gearvia_record_image_state requires a state file"
  for pair in \
    "BACKEND:${GEARVIA_BACKEND_IMAGE:-}" \
    "WEB:${GEARVIA_WEB_IMAGE:-}" \
    "MYSQL:${GEARVIA_MYSQL_IMAGE:-}" \
    "INIT_DATA:${GEARVIA_INIT_DATA_IMAGE:-}"; do
    name="${pair%%:*}"
    tag="${pair#*:}"
    [[ -n "$tag" ]] || continue
    id="$(gearvia_docker image inspect --format '{{.Id}}' "$tag" 2>/dev/null || true)"
    [[ -n "$id" ]] || gearvia_die "Prepared image is unavailable for state recording: $tag"
    gearvia_write_kv "${name}_IMAGE_REF" "$tag" "$state_file"
    gearvia_write_kv "${name}_IMAGE_ID" "$id" "$state_file"
  done
}
