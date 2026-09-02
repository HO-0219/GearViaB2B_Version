#!/usr/bin/env bash

gearvia_is_private_ipv4() {
  local address="$1" a b c d
  [[ "$address" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]] || return 1
  a="${BASH_REMATCH[1]}"; b="${BASH_REMATCH[2]}"; c="${BASH_REMATCH[3]}"; d="${BASH_REMATCH[4]}"
  (( 10#$a <= 255 && 10#$b <= 255 && 10#$c <= 255 && 10#$d <= 255 )) || return 1
  (( 10#$a == 10 || (10#$a == 172 && 10#$b >= 16 && 10#$b <= 31) || (10#$a == 192 && 10#$b == 168) ))
}

gearvia_detect_primary_address() {
  local candidates="" candidate route_address=""
  if [[ -v GEARVIA_TEST_IP_CANDIDATES ]]; then
    candidates="$GEARVIA_TEST_IP_CANDIDATES"
  else
    if command -v ip >/dev/null 2>&1; then
      route_address="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')"
      candidates="$route_address $(ip -o -4 address show scope global 2>/dev/null | awk '{print $4}')"
    fi
    if command -v hostname >/dev/null 2>&1; then
      candidates="$candidates $(hostname -I 2>/dev/null || true)"
    fi
  fi

  for candidate in $candidates; do
    candidate="${candidate%/*}"
    if gearvia_is_private_ipv4 "$candidate"; then
      printf '%s' "$candidate"
      return
    fi
  done
  gearvia_die "사설 IPv4 주소를 감지하지 못했습니다. 호스트의 고정 IP 또는 네트워크 설정을 확인하십시오"
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

gearvia_issue_server_cert() {
  local tls_dir="$1" address="$2" host_name="$3"
  gearvia_is_private_ipv4 "$address" || gearvia_die "Server certificate address must be a private IPv4 address"
  [[ "$host_name" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || gearvia_die "Server hostname is invalid"
  gearvia_generate_local_ca "$tls_dir"

  if [[ -f "$tls_dir/privkey.pem" && -f "$tls_dir/fullchain.pem" ]] \
    && openssl x509 -in "$tls_dir/fullchain.pem" -noout -checkip "$address" >/dev/null 2>&1 \
    && openssl x509 -in "$tls_dir/fullchain.pem" -noout -checkhost "$host_name" >/dev/null 2>&1 \
    && openssl x509 -in "$tls_dir/fullchain.pem" -noout -checkhost localhost >/dev/null 2>&1 \
    && openssl x509 -in "$tls_dir/fullchain.pem" -noout -checkip 127.0.0.1 >/dev/null 2>&1; then
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
