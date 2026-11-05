# Upohar Lab

A small Bangla ordering website using Java 21, Spring Boot, Thymeleaf and PostgreSQL.

Phases 1–3 provide the application foundation, admin login, product and photo editing,
business settings, home/product pages, the customer order form and local Docker setup.
Existing products remain draft until you complete and publish them.

## Run locally with Docker

Install and start Docker Desktop. On first setup:

```sh
cp .env.example .env
```

Set a private `DB_PASSWORD`, `ADMIN_USERNAME` and `ADMIN_PASSWORD` in `.env` using your editor.
The first admin password must have 12–72 characters. Other local values can stay as supplied.
If `.env` already exists, keep it. Never commit it.

```sh
docker compose up --build -d
```

Open **http://localhost:8080**. If the port is in use, change `APP_PORT` in `.env`.
If the database port is occupied, change `DB_HOST_PORT` in `.env` as well.
The first build downloads dependencies and base images.

```sh
docker compose logs --tail=80 app
docker compose down
```

Stopping with `down` preserves the database and uploads volumes. Do not add `-v` unless you
intend to delete local data. This Compose file binds to localhost and is for development.
For production with HTTPS and backups, see [DEPLOY.md](DEPLOY.md).

## Run with Java 21 on your computer

The Maven Wrapper downloads Maven automatically. It requires Java 21 (not Java 17).
On macOS, choose the installed Java 21 for this terminal:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Start only PostgreSQL, then run the app:

```sh
docker compose up -d postgres
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_HOST_PORT:-5433}/${DB_NAME:-upoharlab}"
./mvnw spring-boot:run
```

The sample `.env` uses shell-compatible values. Keep custom values shell-quoted when needed.
For Windows, set the same environment variables and use `mvnw.cmd`.
Stop the Compose app first if it already uses port 8080. Local PostgreSQL is available only on
127.0.0.1:5433 by default; the app container uses the internal postgres:5432 address.

## Checks

With Java 21 and Docker running:

```sh
./mvnw verify
```

Tests start an isolated PostgreSQL Testcontainer, independent of your local `.env` database.
They cover Bangla/Latin phone formats, invalid phones, Bangla money/date formatting,
Dhaka month boundaries, Flyway seed safety, database constraints, home rendering and closed
admin routes. A Docker failure is a failed integration check, not silently skipped.

Flyway runs migrations once. Later startups validate their checksums and do not overwrite
product/settings edits. Modify schema with a new migration, not by editing an applied file.
Hibernate schema validation is enabled; the initial Product mapping is checked at startup.
Other launch tables are created now and will receive application mappings as their phases are built.

## Structure and versions

- Java language/runtime target: **21**.
- Spring Boot: **4.1.2**, pinned in `pom.xml`; see [official requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- Maven: **3.9.11**, Wrapper **3.3.4**.
- PostgreSQL image: **16.15-alpine**, used in Compose and tests.
- Docker Java images: Eclipse Temurin **21.0.13_9-jdk-jammy / 21.0.13_9-jre-jammy**.
- Caddy image: **2.10.2-alpine** (production only).
- Flyway, Hibernate, Spring Security, JUnit and Testcontainers versions are managed by the Spring Boot BOM.
- Noto Sans Bengali is self-hosted as a Bengali + Latin WOFF2 subset; its SIL Open Font License is in `static/fonts/OFL.txt`.

`src/main/java/com/upoharlab` contains the application, security/time configuration, Product
mapping and shared helpers. Templates use header/footer fragments and one plain stylesheet.
`src/main/resources/db/migration` contains the schema and draft-only seed migration.

Database timestamps and JVM/Hibernate storage use UTC. Business-date formatting and the injected
business clock use `Asia/Dhaka`. SQL logging of parameters is not enabled.

The app uses the `/data/uploads` persistent volume for later photo uploads. Secrets use environment
variables. The first account is created from `ADMIN_USERNAME` and `ADMIN_PASSWORD` only when the admin
table is empty. Later restarts keep the existing password. CSRF stays enabled on every form; login is rate-limited by
username and IP. Admin pages are uncached and marked noindex. Unimplemented routes remain closed.

The `prod` profile enables secure cookies and reads client addresses only from Caddy. It refuses
to start without an `https://` site address. See [DEPLOY.md](DEPLOY.md).

## Use the Phase 2 admin

Open **http://localhost:8080/admin/login**. For this local workspace, a private bootstrap
username/password were generated in the ignored `.env` file. Read `ADMIN_USERNAME` and
`ADMIN_PASSWORD` there with your editor; they are not printed in logs or committed.
Keep that file private. After the first bootstrap, editing its password does not reset
the database account. A missing first-boot credential causes a clear startup failure.

- **Products:** edit a draft, save, add real JPEG/PNG photos and their Bangla descriptions,
  then complete the required details and switch to Active. Use Preview to inspect drafts.
- **Photos:** up to 5 MB and 20 megapixels. Phone rotation is applied, then photos are stored as
  resized JPEGs with random filenames, full and thumbnail versions. The original filename and metadata are discarded.
- **Availability:** Draft is private, Active appears in the home grid, Paused keeps the
  public detail page with a pause message. Indoor-only warnings stay enabled once set.
- **Settings:** contacts, bKash, delivery charges, making-independent courier durations,
  reply hours and shop pause notice. All required business details must be valid before
  enabling the order-taking setting. Unknown optional values may stay empty.

## Ordering (Phase 3)

The order form appears on active products once **Accepting new orders** is enabled in Settings.
Customers pay nothing on the website. The server recalculates every amount, and saves an order
only after the customer has seen the current total; if prices change first, they review it again.

Set `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in `.env` for optional owner alerts, and
`APP_BASE_URL` to include an admin link in them. Alerts never contain a phone or address, and a
failed alert does not affect the order. `ORDERS_PER_HOUR` limits new orders per IP address.


## Managing orders (Phase 4)

Open **Orders** in the admin. Search by code or phone, then work an order through its steps:

`NEW` → `DESIGN_SENT` → `CONFIRMED` → `MAKING` → `SHIPPED` → `DELIVERED`, or `CANCELLED` before shipping.

- **Confirming** needs written approval recorded and verified payments covering the advance.
- **Payments:** check your bKash account or the actual settlement first, then record it. A customer's
  screenshot alone is not verification. Overpayments, excess refunds and repeated transaction
  references are rejected. Delivered does not mean paid, so record the balance when it arrives.
- **Cancelling** shows the money you still hold. Refund outside the website, then record the refund.
- **Corrections:** name, phone and address can change before shipping. Product, quantity, engraving and
  delivery area cannot; cancel and take a new agreed order instead.
- **WhatsApp buttons** only open a prepared message. Nothing is ever sent automatically.
No customer or business data has been invented to make the shop appear live.

Admin form login follows [Spring Security's official form-login guidance](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html).
