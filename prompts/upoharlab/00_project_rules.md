# Upohar Lab: shared project rules

Read this with every phase. It defines the launch scope and resolves conflicting details in other prompts.

## 1. Goal and scope

Build a useful, calm, mobile-friendly personalized-gift shop with **Spring Boot, Thymeleaf and PostgreSQL**. Customers browse, order without an account and track their order. One admin manages products, fulfilment, payment records and settings.

Minimal means fewer decisions, clear wording and a short order flow. Keep enough product information to buy confidently. Do not shrink the site into an empty landing page or make desktop users read everything in a narrow mobile column.

Build the requested phase, or all phases when the user requests the full website. Resolve routine details yourself and keep working. Ask only about missing information that blocks progress. Report actual checks and blockers; do not add speculative feature proposals to every report.

## 2. Business decisions

- Brand: **Upohar Lab (উপহার ল্যাব)**; domain: `upoharlab.com`; tagline: **আপনার নাম, যত্নে খোদাই করা**.
- Four separate products: single engraved keyring, two-keyring gift pair, indoor door name plate 5 × 10 inch, indoor desk name plate.
- Trial prices: pair ৳399, door plate ৳899. Trial delivery: inside Dhaka ৳60, outside ৳110. These are draft values, editable and checked before publishing. Other prices remain unset.
- Process: website order → WhatsApp design → written approval → 50% bKash advance → making → courier → remaining payment on delivery.
- One product type per order, quantity 1–10. A quantity of 1 for the pair means one pair. Bulk orders use WhatsApp or phone.
- Advance is half the total including delivery, rounded up to whole taka. No payment is due at website submission.
- Making time starts after approval and verified advance. Show making and courier durations separately; do not promise a date calculated from submission day.
- Products may be paused. A global `accepting_orders` switch and a short notice handle temporary closures without a scheduling system.

## 3. Stack and structure

- Java 21, a maintained stable compatible Spring Boot release, Maven Wrapper. Pin the chosen version after checking official compatibility; do not describe all of Spring Boot 3.x as “latest”.
- Spring MVC, Thymeleaf fragments, JPA, Flyway, PostgreSQL 16 with maintenance updates, Spring Security and Bean Validation.
- Plain CSS and small vanilla JavaScript enhancements. No frontend build pipeline or HTMX dependency. Normal forms work without JavaScript.
- One deployable app. Organise Java code by `product`, `order`, `admin`, `settings`, `config` and small shared helpers. No generic platform or microservices.
- Store product images on a persistent `/data/uploads` volume. Keep database schema changes in Flyway; use `ddl-auto=validate` and `open-in-view=false`.
- Use Boot-managed compatible test dependencies. Use PostgreSQL Testcontainers for transactions, uniqueness and migrations.

## 4. Customer UX

- Natural Bangla, `<html lang="bn">`, Bangla digits for displayed money/counts/dates. Keep order codes and phone links in their copyable standard form. Accept Bangla and Latin digits in forms.
- Warm cream background, dark text and terracotta accents. Suggested tokens: `--bg:#F7F1E8`, `--surface:#FFFDF9`, `--text:#2B211C`, `--muted:#6B5A4E`, `--primary:#A44922`, `--border:#E2D4C0`.
- One readable Bangla font with fallback; about 17px body text and generous line height. No arbitrary CSS line-count limit.
- Main content may grow to about 1100px; long prose stays around 720px and forms around 640px. Use a responsive product grid and a photo/details split on wide screens. Stack naturally on phones.
- No horizontal page scroll at 320px. Use at least 48px-tall primary buttons, visible labels/focus, useful image alt text and accessible contrast.
- Header: brand, products/home, tracking and help. Footer: contact and policies. One primary action per section.
- Put essential product facts and the order form before long FAQs. Optional details can use `<details>`. Do not hide prices, delivery costs or payment timing in accordions.
- Prefer an inline order summary. Do not add a fixed mobile bar that covers inputs, the keyboard or the submit button.

## 5. Launch routes

| Customer | Purpose |
|---|---|
| `GET /` | Short introduction, product grid, order steps |
| `GET /p/{slug}` | Product details and order form |
| `POST /p/{slug}/order` | Update summary or submit, distinguished by form action |
| `GET /order/{code}/success` | Confirmation available only to the submitting session |
| `GET /track`, `POST /track` | Code plus full phone number; limited public status |
| `GET /bulk` | Bulk guidance and WhatsApp/phone contact; no request database |
| `GET /faq`, `/about`, `/policies/delivery`, `/policies/returns`, `/policies/privacy` | Brief useful information |
| `GET /sitemap.xml`, `/robots.txt` | Public-page discovery |

| Admin | Purpose |
|---|---|
| `/admin/login`, `/admin/logout` | Login and POST logout |
| `/admin` | Three operational counts and recent orders |
| `/admin/orders`, `/admin/orders/{code}` | Search/list and fulfilment |
| `/admin/products`, `/admin/products/{id}` | Product list/editor |
| `/admin/settings` | Non-secret business details |

All admin mutations use POST and require authentication. No public account pages.

## 6. Reliability and privacy

- Money is integer whole taka; no floating-point calculations. One `PricingService` serves both the page and submission. Store order price and title snapshots.
- Generate codes as `UL-YYYYMM-NNN` in `Asia/Dhaka`, atomically and safely under concurrent submissions. Allow more than 999 orders. Store timestamps as `timestamptz`.
- All POST forms use CSRF. Validate on the server and keep typed values after errors. Escape content with `th:text`; no raw HTML from customers or product editors.
- Use a session-bound submit token with database uniqueness so repeat clicks create one order. Confirmation requires that same session's access to the order; otherwise offer tracking without exposing order data.
- Track using order code plus the **full phone number**, normalized on the server. Show only status, dates and courier details; no names, addresses, payment references or engraving text.
- Phone/address are private: visible in the customer's own form response and authenticated order detail only. Mask phones in admin lists. Never include them in notifications, logs or query strings.
- Admin contact buttons may open `wa.me/<customer phone>` deliberately; admin pages send `Referrer-Policy: no-referrer`. Public WhatsApp buttons use the business number.
- Set `Cache-Control: no-store` on form responses containing personal data, confirmations, tracking results and admin pages. Mark private routes `noindex`. Robots rules are not access control.
- Rate-limit login, order submissions and tracking with a bounded in-memory limiter suitable for one app instance. Trust forwarded IP headers only from the reverse proxy. Do not persist raw visitor IPs or log form bodies.
- Admin passwords use BCrypt. No default credentials; bootstrap the first account from environment variables. Secrets stay out of source code and business settings.

## 7. Content and honesty

Home copy: **আপনার প্রিয়জনের নাম, যত্নে খোদাই করা** / **নামের চাবির রিং, বাসা ও অফিসের নেমপ্লেট। বানানোর আগে ডিজাইন দেখাব।**

Order steps: **পণ্য ও লেখা বাছুন → ডিজাইন দেখে সম্মতি দিন → অগ্রিম দিন → উপহার বুঝে নিন**.

Payment note: **এখন টাকা দিতে হবে না। আগে WhatsApp-এ ডিজাইন দেখাব। আপনি সম্মতি দিলে ৫০% অগ্রিম দেবেন।**

Never invent reviews, counts, urgency, product photos, availability, phone numbers or policy promises. Name plates clearly say **শুধু ঘরের ভেতরের জন্য**. Use only real product claims.

Draft concise delivery, revision, cancellation, damage and privacy wording from known facts. Leave unconfirmed terms for owner review before launch; do not publish invented refund/revision guarantees. Missing optional content should disappear without empty sections.

## 8. Explicitly outside this version

Customer accounts, cart, online payment gateway, live name preview, font management, product options/variants engine, paid add-ons, discounts/offers, countdowns, reviews/ratings, sales counters, capacity scheduling, staff roles, analytics/revenue dashboards, exports, bulk CRM, SMTP/webhooks and CI/CD servers.

Do not create their tables, routes or placeholder menus. A simple optional Telegram notification may be added in Phase 3 when credentials exist; it must never be required to place an order.
