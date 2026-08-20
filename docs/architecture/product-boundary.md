# Product Boundary

## Purpose

B2BGearVia starts from a copied source snapshot, but it is now a separate product line with a different deployment model, operating environment, and feature boundary. The goal of this repository is to deliver a single-company on-premise collaboration product for Ubuntu servers, not to continue the original SaaS service.

## Deployment boundary

- Target runtime: Ubuntu Server 24.04 LTS x86_64
- Target topology: one company on one server
- Target packaging: installer-driven Docker deployment
- Repository baseline: local-only Git repository with no remote configured
- Workflow boundary: no active GitHub Actions workflow is left in `.github/workflows` at this baseline

## Functional boundary

The B2B transition keeps the core collaboration domain that already exists in the snapshot:

- groups, roles, tasks, approvals, checklists, comments, and mentions
- calendars, notifications, dashboards, reports, and PDFs
- projects, project issues, emergency issues, chat, resources, and document uploads
- JWT sessions, device sessions, logout, administrator MFA foundations, Flyway migrations, and health checks

The transition plan removes or replaces SaaS-specific product edges:

- public sign-up and public pricing surfaces
- social sign-in and demo access
- self-serve subscriptions, billing, trials, and payment integrations
- deployment assumptions tied to public EC2 hosting and the original public domain

## Operational boundary

- Administrators, not end users, are responsible for server preparation, Docker installation, and updates.
- Installation and updates require outbound HTTPS access.
- Core collaboration should continue to operate without constant outbound internet access after installation.
- AI, email, and push integrations stay optional and must not be treated as install blockers.

## Source-control boundary

- This repository must not point at the original product remotes.
- Historical workflow files must not preserve original public-domain or EC2 deployment automation.
- Baseline documentation should explain the supported environment and unsupported deployment paths before further product changes begin.
