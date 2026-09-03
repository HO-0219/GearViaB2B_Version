#!/usr/bin/env bash

gearvia_is_private_ipv4() {
  local address="$1" a b c d
  [[ "$address" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]] || return 1
  a="${BASH_REMATCH[1]}"; b="${BASH_REMATCH[2]}"; c="${BASH_REMATCH[3]}"; d="${BASH_REMATCH[4]}"
  (( 10#$a <= 255 && 10#$b <= 255 && 10#$c <= 255 && 10#$d <= 255 )) || return 1
  (( 10#$a == 10 || (10#$a == 172 && 10#$b >= 16 && 10#$b <= 31) || (10#$a == 192 && 10#$b == 168) ))
}

gearvia_detect_primary_address() {
  local candidates="" candidate route_address="" nat_fallback=""

  # An explicit override always wins — the operator knows which address the
  # instance is actually reached on when autodetection cannot.
  if [[ -n "${GEARVIA_PUBLIC_ADDRESS:-}" ]]; then
    gearvia_is_private_ipv4 "$GEARVIA_PUBLIC_ADDRESS" \
      || gearvia_die "GEARVIA_PUBLIC_ADDRESS must be a private IPv4 address: $GEARVIA_PUBLIC_ADDRESS"
    printf '%s' "$GEARVIA_PUBLIC_ADDRESS"
    return
  fi

  if [[ -v GEARVIA_TEST_IP_CANDIDATES ]]; then
    candidates="$GEARVIA_TEST_IP_CANDIDATES"
  else
    if command -v ip >/dev/null 2>&1; then
      route_address="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')"
      # Drop container/bridge interfaces (docker0, br-*, veth*, virbr*): their
      # addresses are private but never how an operator reaches the host.
      candidates="$route_address $(ip -o -4 address show scope global 2>/dev/null | awk '$2 !~ /^(docker|br-|veth|virbr)/ { print $4 }')"
    fi
    if command -v hostname >/dev/null 2>&1; then
      candidates="$candidates $(hostname -I 2>/dev/null || true)"
    fi
  fi

  for candidate in $candidates; do
    candidate="${candidate%/*}"
    gearvia_is_private_ipv4 "$candidate" || continue
    # A VirtualBox NAT adapter always holds 10.0.2.15/24 and carries the guest's
    # default route, so "ip route get" reports it first — but it is reachable
    # only from inside the guest, never from the host or the LAN. Bake it into
    # FRONTEND_URL and every request from the real address is a CORS 403. Accept
    # it only when the host-only / LAN address cannot be found.
    if [[ "$candidate" == 10.0.2.* ]]; then
      [[ -n "$nat_fallback" ]] || nat_fallback="$candidate"
      continue
    fi
    printf '%s' "$candidate"
    return
  done

  if [[ -n "$nat_fallback" ]]; then
    printf '%s' "$nat_fallback"
    return
  fi
  gearvia_die "사설 IPv4 주소를 감지하지 못했습니다. GEARVIA_PUBLIC_ADDRESS 로 접속 주소를 지정하거나 네트워크 설정을 확인하십시오"
}

gearvia_generate_local_ca() {
  local tls_dir="$1"
  mkdir -p "$tls_dir"
  chmod 0700 "$tls_dir"
  if [[ -f "$tls_dir/ca.key" || -f "$tls_dir/ca.crt" ]]; then
    [[ -f "$tls_dir/ca.key" && -f "$tls_dir/ca.crt" ]] || gearvia_die "Local CA key and certificate must both exist"
    chmod 0600 "$tls_dir/ca.key"
    chmod 0644 "$tls_dir/ca.crt"
    return
  fi

  (
    umask 077
    MSYS2_ARG_CONV_EXCL='/CN=' openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 3650 \
      -subj '/CN=GearVia Local CA' -keyout "$tls_dir/ca.key.new" -out "$tls_dir/ca.crt.new" >/dev/null 2>&1
    chmod 0600 "$tls_dir/ca.key.new"
    chmod 0644 "$tls_dir/ca.crt.new"
    mv "$tls_dir/ca.key.new" "$tls_dir/ca.key"
    mv "$tls_dir/ca.crt.new" "$tls_dir/ca.crt"
  )
}

# "openssl x509 -checkip / -checkhost" always exit 0 — they only report the
# result as text on stdout. Inspect that text so a stale certificate (for
# example after the host address changes and the installer is re-run) is
# actually re-issued instead of silently kept.
gearvia_cert_covers() {
  local cert="$1" flag="$2" value="$3" output
  output="$(openssl x509 -in "$cert" -noout "$flag" "$value" 2>/dev/null)" || return 1
  [[ "$output" == *"does match certificate"* ]]
}

gearvia_issue_server_cert() {
  local tls_dir="$1" address="$2" host_name="$3"
  gearvia_is_private_ipv4 "$address" || gearvia_die "Server certificate address must be a private IPv4 address"
  [[ "$host_name" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || gearvia_die "Server hostname is invalid"
  gearvia_generate_local_ca "$tls_dir"

  if [[ -f "$tls_dir/privkey.pem" && -f "$tls_dir/fullchain.pem" ]] \
    && gearvia_cert_covers "$tls_dir/fullchain.pem" -checkip "$address" \
    && gearvia_cert_covers "$tls_dir/fullchain.pem" -checkhost "$host_name" \
    && gearvia_cert_covers "$tls_dir/fullchain.pem" -checkhost localhost \
    && gearvia_cert_covers "$tls_dir/fullchain.pem" -checkip 127.0.0.1; then
    chmod 0600 "$tls_dir/privkey.pem"
    chmod 0644 "$tls_dir/fullchain.pem"
    return
  fi

  (
    umask 077
    local csr="$tls_dir/server.csr.$$" extension_file="$tls_dir/server.ext.$$"
    trap 'rm -f -- "$csr" "$extension_file" "$tls_dir/fullchain.pem.new" "$tls_dir/privkey.pem.new"' EXIT
    if [[ ! -f "$tls_dir/privkey.pem" ]]; then
      openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$tls_dir/privkey.pem.new" >/dev/null 2>&1
      chmod 0600 "$tls_dir/privkey.pem.new"
      mv "$tls_dir/privkey.pem.new" "$tls_dir/privkey.pem"
    fi
    printf '%s\n' \
      'basicConstraints=critical,CA:FALSE' \
      'keyUsage=critical,digitalSignature,keyEncipherment' \
      'extendedKeyUsage=serverAuth' \
      "subjectAltName=IP:$address,DNS:$host_name,DNS:localhost,IP:127.0.0.1" > "$extension_file"
    MSYS2_ARG_CONV_EXCL='/CN=' openssl req -new -sha256 -key "$tls_dir/privkey.pem" -subj "/CN=$host_name" -out "$csr" >/dev/null 2>&1
    openssl x509 -req -sha256 -days 825 -in "$csr" \
      -CA "$tls_dir/ca.crt" -CAkey "$tls_dir/ca.key" -CAcreateserial \
      -extfile "$extension_file" -out "$tls_dir/fullchain.pem.new" >/dev/null 2>&1
    chmod 0600 "$tls_dir/privkey.pem"
    chmod 0644 "$tls_dir/fullchain.pem.new"
    mv "$tls_dir/fullchain.pem.new" "$tls_dir/fullchain.pem"
  )
}
