# Phase 7: deployment and recovery

Follow `00_project_rules.md`. Deliver one understandable deployment path: a VPS running Docker Compose with the app, PostgreSQL and Caddy.

Prepare and validate the files first. When the user has requested deployment and the target/access are already authorized, proceed without asking again. If access is missing, finish the preparation and report exactly what is needed. Do not buy hosting, choose a paid plan or replace an unrelated existing service without instruction.

## Deployment files

- Multi-stage Dockerfile using the Maven Wrapper and Java 21; run the app as a non-root user with a writable uploads volume.
- Production Compose: `app`, PostgreSQL 16 with current maintenance updates, and Caddy. Pin chosen app/base-image versions and document them; do not use floating `latest` tags.
- Persistent volumes for PostgreSQL, `/data/uploads` and Caddy certificate data. Add health checks and restart policies. Start the app only when PostgreSQL is healthy; let Flyway validate/apply migrations.
- A production configuration with required environment settings validated on startup, secure session cookies and bounded database connections. Set a JVM heap limit appropriate to the measured server resources; do not guarantee a 1 GB server will work without testing total memory use.
- Caddy serves `upoharlab.com` over HTTPS and redirects `www` to the main domain. Use supported built-in compression; no custom plugins. Both DNS records must point to the chosen server.
- Expose only Caddy's HTTP/HTTPS ports. App, database and health endpoints stay internal. If Actuator is used, expose only health internally and block it at the proxy.
- `.env.example` documents required settings without secrets. Ignore the real `.env`; restrict its permissions. Keep bootstrap credentials out of logs and remove them after the first account is created.

## Network and browser checks

Allow web traffic and preserve existing SSH access when configuring the firewall. Trust forwarded headers only from Caddy; confirm rate limits see the real client address. Add suitable security headers including `nosniff`, a CSP matching actual assets and HSTS once HTTPS is verified.

Verify public pages, login, uploads and the full order path through the HTTPS domain. Restart the stack and confirm orders and photos remain. Check memory and basic responsiveness under a small representative workload; report the actual result.

## Backups and restore

Provide simple scripts and a daily schedule for PostgreSQL dumps plus the uploads directory. Keep 14 daily successful backups. Copy to an owner-provided private off-server destination with restricted access; backups contain personal data. Preserve previous successful backups if a new run fails, and log a clear failure. Telegram alerts may be used if already configured.

For a consistent database-plus-files backup, briefly pause mutations or use a documented consistent snapshot approach. Do not make public backup archives. Protect secrets separately; never include them in public artifacts or logs.

Run the restore script into a separate scratch database/uploads directory. Verify an order and product image can be recovered; never overwrite production to test restore. A script existing is not evidence of a successful backup/restore.

## Operating guide and completion

Write concise commands for first start, migrations, viewing redacted logs, backup, restore, updating the app and rollback. Take a backup before updates. Explain that an older app image may not work with a newer database schema; rollback must account for migration compatibility.

The report distinguishes:

- **Prepared:** deployment files and local checks complete.
- **Deployed:** app actually reachable on the real HTTPS domain.
- **Verified:** production smoke checks and a separate restore test completed.

List any missing server, DNS or backup access. Never mark this phase deployed based only on Docker files or localhost. Do not add Jenkins, Kubernetes, extra domains, email hosting or Search Console setup to the launch requirements.
