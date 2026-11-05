# Phase 1: application, database and layout

Follow `00_project_rules.md`. Build a small foundation for the launch features only.

## Tasks

1. Set up Maven Wrapper, Java 21 and the chosen compatible Spring Boot dependencies. Record exact versions in the application README.
2. Add local Docker Compose for the app and PostgreSQL, persistent database/uploads volumes, `.env.example`, and `.gitignore` entries for secrets, uploads, build output and `.DS_Store`. Production Caddy configuration comes in Phase 7.
3. Add Flyway migrations, Thymeleaf header/footer fragments, one stylesheet and a temporary home page. Development may use local HTTP cookies; production must require secure cookies.
4. Add Bangla number display and phone normalization helpers. Accept spaces, hyphens, Bangla digits and `+880`/`880` phone prefixes; normalize to `01XXXXXXXXX` and validate BD mobile format.
5. Use `Asia/Dhaka` for business dates and code months, UTC instants in storage, `open-in-view=false` and `ddl-auto=validate`.

## Data model

Keep fields practical; no tables for future features. Money uses whole-taka integers and timestamps use `timestamptz`.

| Table | Necessary fields |
|---|---|
| `product` | id, unique slug, title_bn, short_description_bn, description_bn, includes_bn, material_bn, size_bn, engraving_help_bn, care_bn (optional), indoor_only, price (nullable in draft), making_days_min/max, status (`DRAFT/ACTIVE/PAUSED`), sort_order, timestamps |
| `product_image` | id, product_id, stored filename, alt_bn, sort_order |
| `orders` | id, unique code, unique submit_token, product_id, product_title_snapshot, quantity, engraving_text, customer_name, phone, area (`INSIDE/OUTSIDE`), address, optional note, unit_price_snapshot, delivery_charge_snapshot, total, advance_required, status, design_approved_at, courier, tracking_no, delivered_at, timestamps |
| `payment` | id, order_id, kind (`PAYMENT/REFUND`), method (`BKASH/CASH/COD`), positive amount, transaction_ref (nullable, unique when present), recorded_by, recorded_at |
| `order_event` | id, order_id, event_type, from_status/to_status (nullable), note (optional), actor, created_at |
| `order_code_counter` | year_month primary key, last_value |
| `setting` | key, value; non-secret business settings only |
| `admin_user` | id, unique username, password_hash, active |

Payment rows represent payments/refunds already verified by the owner. Do not store unverified customer claims as payments. Order events support a short history of status, approval, payment and fulfilment changes, not a generic audit platform.

Add foreign keys, unique constraints, enum checks and sensible quantity/money bounds. Index order status, creation date and phone, plus order references in payments/events. Products referenced by orders cannot be deleted; pause them instead. Use transactions for order creation and fulfilment changes.

## Settings and seed data

Settings: business phone, WhatsApp, bKash number, optional business email, delivery charges by area, courier day ranges by area, reply hours, `accepting_orders`, pause notice. Keep detailed policy text in templates for this version.

Seed all four products as `DRAFT`. Pair price 399; door plate price 899, size 5 × 10 inch and indoor only; desk plate indoor only. Unspecified prices/details remain empty. Seed delivery 60/110 as trial values and `accepting_orders=false` until business setup is complete. No sample customers, orders, reviews or admin passwords.

Admin bootstrap credentials and database credentials come from environment variables. Optional Telegram credentials are also environment-only. Include safe descriptions in `.env.example` without real secrets.

## Done when

- The app starts with PostgreSQL, migrations and the temporary page.
- Re-running migrations does not reseed or overwrite owner edits.
- Bangla renders correctly and the layout fits a 320px viewport.
- Focused tests cover phone normalization, Bangla number formatting and migrations on PostgreSQL.
- The README explains how to start locally and run checks. Any temporary admin routes remain protected until Phase 2 is complete.
