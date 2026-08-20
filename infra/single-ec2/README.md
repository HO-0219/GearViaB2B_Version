# Deprecated

The single-EC2/domain/certbot deployment is retired. Use `infra/b2b/compose.yml`.
Copy `infra/b2b/runtime.env.example` to `infra/b2b/runtime.env`, replace every
placeholder, and run Compose with `--env-file infra/b2b/runtime.env`.
TLS certificates are supplied through paths configured in that file;
there is no public HTTP, certbot, or separate administrator port in the B2B stack.
