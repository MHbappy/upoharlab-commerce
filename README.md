# Upohar Lab

A small Bangla ordering website using Java 21, Spring Boot, Thymeleaf and PostgreSQL.

Customers browse products, order without an account and track their order. One admin manages
products, orders, payment records and business settings. Products stay draft until you complete
and publish them, and ordering stays off until you enable it in Settings.

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

Open **http://localhost:1234**. If the port is in use, change `APP_PORT` in `.env`.
If the database port is occupied, change `DB_HOST_PORT` in `.env` as well.
The first build downloads dependencies and base images.

```sh
docker compose logs --tail=80 app
docker compose down
```

Stopping with `down` preserves the database and uploads volumes. Do not add `-v` unless you
intend to delete local data. This Compose file binds to localhost and is for development.
For production with HTTPS and backups, see [DEPLOY.md](DEPLOY.md).

## Test data for local review

```sh
scripts/demo-data.sh add      # products, photos, contacts and 30 orders in every status
scripts/demo-data.sh remove   # takes it all out again
```

This changes only the local `compose.yaml` database and is not a migration, so it cannot reach production.
All customers, numbers and addresses are made up, and the photos are labelled as samples. The command
prints order codes and phones you can try on the tracking page. Don't press send on WhatsApp buttons
for test orders, because a made-up number may belong to a real person.

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
Stop the Compose app first if it already uses port 1234. Local PostgreSQL is available only on
127.0.0.1:5433 by default; the app container uses the internal postgres:5432 address.

### Personal settings for an IDE or another database

To run from an IDE, or against a different database, put your values in `application-local.yml` in
the project root and start with the Spring profile `local` (in IntelliJ: Run configuration → Active
profiles → `local`). The file is ignored by Git and Docker, so it never reaches an image or production.
Never put credentials in `src/main/resources/application.yml`; a test fails if a password appears there.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/upoharlab
    username: upoharlab
    password: your-local-password
app:
  admin:
    username: owner
    password: your-private-password-of-12-to-72-characters
```

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
Hibernate schema validation is enabled for the Product mapping. Orders, payments, settings and
admin accounts use SQL through `JdbcTemplate`, with database constraints as a backstop.

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
mapping and the `product`, `order`, `admin`, `settings`, `config` and `shared` packages. Templates
use header/footer fragments and one plain stylesheet whose colours are tokens at the top of `app.css`.
`src/main/resources/db/migration` contains the schema and draft-only seed migrations.

Database timestamps and JVM/Hibernate storage use UTC. Business-date formatting and the injected
business clock use `Asia/Dhaka`. SQL logging of parameters is not enabled.

In Docker, photos are stored on the `/data/uploads` persistent volume; outside Docker the default
is `./uploads`. Secrets use environment variables. The first account is created from `ADMIN_USERNAME` and `ADMIN_PASSWORD` only when the admin
table is empty. Later restarts keep the existing password. CSRF stays enabled on every form; login is rate-limited by
username and IP. Admin pages are uncached and marked noindex. Unimplemented routes remain closed.

The `prod` profile enables secure cookies and reads client addresses only from Caddy. It refuses
to start without an `https://` site address. See [DEPLOY.md](DEPLOY.md).

## Use the admin

Open **http://localhost:1234/admin/login** and sign in with the `ADMIN_USERNAME` and `ADMIN_PASSWORD`
from your ignored `.env` file. They are not printed in logs or committed.
Keep that file private. After the first bootstrap, editing its password does not reset
the database account. A missing first-boot credential causes a clear startup failure.

- **Products:** enter the details, add up to 12 JPEG/PNG photos, and click Add product to publish in one step. Product links are generated automatically unless customized. Choose Save privately only when you want to finish later. Existing products can save detail changes and new photos together.
- **Photos:** up to 5 MB and 20 megapixels. Phone rotation is applied, then photos are stored as
  resized JPEGs with random filenames, full and thumbnail versions. The original filename and metadata are discarded.
- **Availability:** Draft is private, Active appears in the home grid, Paused keeps the
  public detail page with a pause message. Indoor-only warnings stay enabled once set.
- **Settings:** contacts, bKash, delivery charges, making-independent courier durations,
  reply hours and shop pause notice. All required business details must be valid before
  enabling the order-taking setting. Unknown optional values may stay empty.

## Site text (all customer-facing wording)

Every word customers see — menu, home page, product page, order form, error messages, tracking
statuses, policy pages and the WhatsApp messages the admin sends — can be changed in **Admin → Site
text**, grouped by page with a search box. Empty a box to go back to the default; a few optional texts
(such as the returns notice) can be emptied to hide them. `{0}`-style placeholders are filled in by the
page, for example with a price, and a broken placeholder is refused before anything is saved.

Defaults live in `src/main/resources/site-text.properties`; edits are stored in the `site_text` table
and apply immediately. Templates use them as `#{key}`. To add a text, add a key to that file and use it;
a test fails if a page, script or message refers to a key that does not exist.

In each product's landing page section, **Customer's own text** sets whether customers write something
on that product: *Yes, always* (the original flow), *If they like* (the box is optional), or *No* (a
ready-made product: no text box, live preview, font or design choice). The same section takes the
product's own words for the text field and preview, for example "কী প্রিন্ট হবে" on a mug.

Only orders with customer text have a design step. An order without text — from a *No* product, or an
*If they like* product left blank — skips "Design sent" and approval: the admin confirms it straight
from New once any advance is recorded, and the customer sees wording that promises no design.

## Ordering

The order form appears on active products once **Accepting new orders** is enabled in Settings.
Customers order without an account using their name, phone and address; engraving instructions are optional and can be agreed afterwards. Admins can record missing engraving details on the order before design approval. The server recalculates every amount, and saves an order
only after the customer has seen the current total; if prices change first, they review it again.

Set `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in `.env` for optional owner alerts, and
`APP_BASE_URL` to include an admin link in them. Alerts never contain a phone or address, and a
failed alert does not affect the order. `ORDERS_PER_HOUR` limits new orders per IP address.

## Landing pages

Every product sells through a one-page order site at `/order/{link}`, built entirely from the **Landing page** section of the product editor: hero, benefits, social proof, three packages, options, extras, advance, delivery charges and preview designs with their fonts. No template changes are needed for a new product. A product cannot be published without one, and older `/p/{link}` addresses redirect there.

Add images with the upload box beside each image slot, or choose one already saved, and use **Preview page** to check a draft. Reviews, counts and the urgency line are hidden until filled in; enter only real ones.

Confirming a landing order needs both design approval and the advance recorded as a payment. The order page shows **Download SVG**, which holds only the text at its real position; laser software needs that font installed (files are in `static/fonts`). To add a font, add its WOFF2 file and an entry in `LandingFonts` and `landing.css`.

Set the Pixel ID in **Admin → Settings → Facebook ads tracking** to turn on browser PageView, ViewContent, InitiateCheckout and Purchase events. `FACEBOOK_PIXEL_ID` in `.env` is used only when that box is empty. Empty everywhere means no Facebook code or address is used.

Add a Conversions API access token there to also send ViewContent and Purchase from the server. They share event IDs with the browser, so Facebook counts each once. Purchase includes the customer's SHA-256 hashed phone, IP address and browser details; the privacy page says so automatically. The token is stored in the `setting` table, is never shown again and is kept out of page data. **Send test event to Facebook** checks the setup using your test event code. Live events never carry that code.

## Managing orders

Open **Orders** in the admin. Search by code or phone, then work an order through its steps:

`NEW` → `DESIGN_SENT` → `CONFIRMED` → `MAKING` → `SHIPPED` → `DELIVERED`, `CANCELLED` before shipping, or `RETURNED` from Shipped or Delivered when a parcel comes back.

The **Dashboard** lists what needs you (designs to send, advances to collect, delivered money to collect, money to refund) and the month's money. **Orders** filters by status, work queue and date, and searches code, phone, name or courier. Tick orders to start making, mark delivered, record full COD received or print packing slips. **Download CSV** exports the current list. **Add order** enters phone or Facebook orders.

- **Corrections:** the latest status step can be undone with a reason. A wrong money record can be voided with a reason; it stays in the list, stops counting and frees its reference.
- **Confirming** needs customer design approval recorded when the order has a design, and any advance recorded in full. The rest is collected on delivery.
- **Payments:** check your bKash account or the actual settlement first, then record it. A customer's
  screenshot alone is not verification. Overpayments, excess refunds and repeated transaction
  references are rejected. Delivered does not mean paid, so record the balance when it arrives.
- **Cancelling** shows the money you still hold. Refund outside the website, then record the refund.
- **Corrections:** name, phone and address can change before shipping. Product, quantity, engraving and
  delivery area cannot; cancel and take a new agreed order instead.
- **WhatsApp buttons** only open a prepared message. Nothing is ever sent automatically.
No customer or business data has been invented to make the shop appear live.

Admin form login follows [Spring Security's official form-login guidance](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html).
