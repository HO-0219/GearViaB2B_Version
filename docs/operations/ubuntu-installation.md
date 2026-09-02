# Ubuntu Installation and Removal

## Supported host

- Ubuntu Server 24.04 LTS on x86_64
- Docker Engine and Docker Compose v2 installed from the approved corporate repository
- A complete GearVia release bundle, TLS certificate/key, fixed IP or internal DNS, and outbound
  internet blocked by default
- NAS mounted at `/opt/b2bgearvia/data/nas` before selecting NAS storage

The default `COMPOSE_PROFILES=bundled-db` starts the packaged MySQL server. For an external MySQL
8.x-compatible server, clear `COMPOSE_PROFILES` and set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`;
the URL must require certificate and hostname verification. Complete the in-product preflight,
notice, migration, and rollback workflow before changing the production runtime file.

## Install or upgrade

Prepare `runtime.env` from `infra/b2b/runtime.env.example`. Replace every placeholder and keep the
file mode at `0600`. Validate without changing the host:

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh --dry-run \
  --config /secure/gearvia/runtime.env
```

Install and start the systemd-managed Compose stack:

```bash
sudo ./install_gearvia_ai_agent_ubuntu.sh \
  --config /secure/gearvia/runtime.env \
  --tls-cert /secure/gearvia/fullchain.pem \
  --tls-key /secure/gearvia/privkey.pem
```

The operation is rerunnable. Configuration is copied to `/etc/gearvia/runtime.env`, deployment
files to `/opt/b2bgearvia`, and recovery state to `/var/lib/gearvia`. The source config is never
evaluated as shell code by the installer.

## Remove

Default removal stops the service and removes application configuration while retaining Docker
database volumes, local/NAS files, and recovery state:

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh
```

Irreversible GearVia-managed file removal requires both flags. Docker named volumes and files on
an externally mounted NAS still require separate storage-owner action:

```bash
sudo ./uninstall_gearvia_ai_agent_ubuntu.sh --purge-data --confirm-purge GEARVIA
```

Run `bash infra/ubuntu/test-lifecycle-scripts.sh` before packaging a release.
