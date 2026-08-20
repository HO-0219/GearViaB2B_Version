# B2BGearVia

B2BGearVia is the isolated on-premise follow-on to the GearVia snapshot in this repository. This repository is intentionally kept separate from the original GearVia Git history, remotes, and deployment automation while the product is converted into a single-company Ubuntu installation package.

## Current repository status

- Local-only Git repository with no configured remote.
- Baseline source snapshot copied from GearVia commit `8647e981bd7b57930fd485965c33e718ff4462b6`.
- Historical GitHub Actions workflows moved out of `.github/workflows` so this baseline cannot trigger the original CI/CD or EC2 deployment path.

## Supported baseline environment

- Ubuntu Server 24.04 LTS x86_64
- Docker Engine installed by the server administrator before running the installer
- Docker Compose v2 installed by the server administrator before running the installer
- HTTPS-capable outbound network access during installation and updates for image and release downloads
- Single-server, single-company deployment target

## Network expectations

- External HTTPS access is required during installation and update workflows.
- Core collaboration features are expected to keep working without general outbound internet access after installation.
- Optional integrations such as OpenAI, SMTP mail delivery, and web push require their own outbound connectivity when enabled.

## Not supported

- Managed SaaS deployment
- Shared multi-tenant hosting
- Non-Ubuntu production targets
- Docker alternatives or manually unmanaged runtime layouts
- Reusing GearVia GitHub remotes, secrets, or deployment workflows

## Repository guidance

- Track repository documentation such as `README.md`, `B2B_TRANSITION_PLAN.md`, and files under `docs/`.
- Keep private working notes under ignored names such as `*.private.md`, `*.notes.md`, or inside ignored local note folders.
- Do not add a remote or publish this repository until B2B-specific workflows and hosting choices are explicitly defined.
