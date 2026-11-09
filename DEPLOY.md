# Deploying Upohar Lab

One VPS running Docker Compose: the app, PostgreSQL 16 and Caddy for HTTPS. Only Caddy is reachable from the internet.

## What you need

- A Linux VPS with Docker Engine and the Compose plugin, and SSH access.
- About 1 GB RAM or more. A local test used roughly 370 MB in total after a light workload (app about 310 MB, PostgreSQL 40 MB, Caddy 20 MB). This has not been measured on a real 1 GB server, so check yours with `docker stats`.
- DNS `A` records for `upoharlab.com` and `www.upoharlab.com` pointing to the server.
- A private off-server backup destination, set up with [rclone](https://rclone.org/) (for example a private Google Drive folder).

## First start

```sh
git clone <your-repo> upoharlab && cd upoharlab
cp .env.example .env
chmod 600 .env
```

Edit `.env`. Production needs `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `SITE_DOMAIN=upoharlab.com` and `APP_BASE_URL=https://upoharlab.com`. Use letters and numbers only in passwords; `openssl rand -hex 24` makes a good one. Keep a copy of `.env` somewhere safe and separate: backups cannot be used for the live site without its database password settings.

Allow web traffic while keeping SSH open. Do this before enabling the firewall:

```sh
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

Start everything. Flyway applies database migrations automatically:

```sh
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
```

After your first login at `https://upoharlab.com/admin/login`, delete `ADMIN_USERNAME` and `ADMIN_PASSWORD` from `.env`, then run the `up -d` command again. Later restarts keep the account.

## Before taking real orders

In the admin, add real contacts and durations in **Settings**, then complete and publish products with real photos and final prices. If a landing page takes an advance, also add your bKash number. Enable **Accepting new orders** last. Place one test order through the live HTTPS site.

## Logs

```sh
docker compose -f compose.prod.yaml logs --tail=100 app
docker compose -f compose.prod.yaml logs --tail=100 caddy
```

The app does not log form contents. Treat logs as private anyway, and never paste secrets from `.env`. Each container keeps at most 30 MB of logs.

## Backups

Install rclone and configure your private destination (`rclone config`). Create the folders once, then add the settings to `.env`:

```sh
sudo install -d -o "$USER" -m 700 /var/backups/upoharlab
sudo install -o "$USER" -m 600 /dev/null /var/log/upoharlab-backup.log
```

```sh
BACKUP_DIR=/var/backups/upoharlab
BACKUP_REMOTE=mydrive:upoharlab-backups
```

Run one now, then prove it can be restored:

```sh
scripts/backup.sh
scripts/restore-check.sh /var/backups/upoharlab/<backup-folder>
```

`restore-check.sh` uses a scratch database and a temporary folder. It never touches production.

Schedule the daily backup with `crontab -e`:

```cron
30 21 * * * cd /home/you/upoharlab && scripts/backup.sh >> /var/log/upoharlab-backup.log 2>&1
```

That is 3:30 AM in Bangladesh on a server set to UTC. Each backup pauses the app for a few seconds so the database and photos match. The 14 newest successful backups are kept, both on the server and off-server, and a failed run leaves them alone. Off-server backups survive even if the server and its backup folder are lost.

Without Telegram, a failed backup only appears in the log, so check it regularly: `tail /var/log/upoharlab-backup.log`. With Telegram configured, a failure also sends an alert.

Backups contain customer data, so keep the destination private. `.env` is not included: store a copy of it somewhere safe and separate.

## Restoring production after a disaster

This replaces live data. Run `restore-check.sh` on the backup first.

On a rebuilt server, first copy `.env` back, fetch the backup with `rclone copy <remote>/<backup-folder> <folder>`, and start only the database with `docker compose -f compose.prod.yaml up -d --build postgres`. Don't start the whole stack yet. The admin account comes back with the backup, so `ADMIN_USERNAME` and `ADMIN_PASSWORD` are not needed.

```sh
docker compose -f compose.prod.yaml stop app caddy
docker compose -f compose.prod.yaml exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose -f compose.prod.yaml exec -T postgres sh -c 'pg_restore --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /path/to/backup/database.dump
docker compose -f compose.prod.yaml run --rm --no-deps -T --entrypoint tar app -C /data/uploads -xzf - < /path/to/backup/uploads.tar.gz
docker compose -f compose.prod.yaml up -d
```

## Updating

Always back up first, and tag each release so you can return to it:

```sh
scripts/backup.sh
git pull
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f compose.prod.yaml up -d --build
```

Then check the home page, login and a product page.

## Rolling back

```sh
git checkout <previous-commit>
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f compose.prod.yaml up -d --build
```

**If the update added a database migration**, older code may refuse to start or misbehave against the newer schema. In that case, also restore the backup taken before the update, using the steps above. Any orders placed since that backup would be lost from the database, so note them first.

## Security notes

- The app trusts client-address headers only from Caddy's fixed private address, so rate limits see real visitors and clients cannot fake their address.
- The app refuses to start in production without an `https://` site address and secure cookies.
- PostgreSQL and the app publish no ports. Check from another machine that only ports 80 and 443 answer.
- Caddy must be the first thing that receives visitors. If you later put a CDN such as Cloudflare in front, rate limits would see the CDN's address instead of each visitor's, so that needs extra setup first.

## When is it really live?

Report each stage separately. Docker files or a localhost test never count as deployed.

- **Prepared:** deployment files and local checks are done.
- **Deployed:** the site answers at `https://upoharlab.com`, and `www` redirects to it.
- **Verified:** on the real domain, you have logged in, uploaded a photo, placed and fulfilled a test order, restarted the stack and found the order and photo still there, run a backup, passed `restore-check.sh`, and confirmed the backup arrived off-server.
