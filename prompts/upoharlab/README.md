# Upohar Lab: simple website build prompts

These prompts describe a complete small ordering website using **Spring Boot, Thymeleaf and PostgreSQL**. The priority is a clear customer journey and easy daily order management.

The review removed launch requirements for scheduling, offers, countdowns, live previews, font management, staff roles and several integrations. Product details, ordering, payments recorded by the owner, tracking, security, deployment and backups remain included.

## How to use

Start with [the overall brief](../../upoharlab-website-prompt.md). The [project rules](00_project_rules.md) apply to every phase; the phase files contain the implementation details. If they disagree, follow the project rules and update the conflicting detail.

For the complete build, use:

```text
Read upoharlab-website-prompt.md and prompts/upoharlab/README.md.
Build the website phase by phase, following 00_project_rules.md throughout.
Keep the agreed launch scope small. Continue through the phases, checking each one
and giving short progress updates. Prepare deployment and backups as part of the work.
Use already-authorized deployment access if available; otherwise report what is missing.
Do not add future features or invent business information.
```

To work on one phase instead:

```text
Read prompts/upoharlab/00_project_rules.md and prompts/upoharlab/01_skeleton_and_data.md.
Build only that phase, check its acceptance criteria and report the result.
```

| Order | File | Result |
|---|---|---|
| Always | [00_project_rules.md](00_project_rules.md) | Scope, shared decisions and design rules |
| 1 | [01_skeleton_and_data.md](01_skeleton_and_data.md) | Application, database, local Docker and layout |
| 2 | [02_admin_foundation_and_products.md](02_admin_foundation_and_products.md) | Admin login, products, settings and shop pages |
| 3 | [03_order_flow.md](03_order_flow.md) | Order form, pricing and confirmation |
| 4 | [04_admin_orders.md](04_admin_orders.md) | Fulfilment, payment records and order history |
| 5 | [05_trust_and_conversion.md](05_trust_and_conversion.md) | Usability and clear product copy |
| 6 | [06_tracking_bulk_info_seo.md](06_tracking_bulk_info_seo.md) | Tracking, contact/help pages and basic SEO |
| 7 | [07_deploy_and_backup.md](07_deploy_and_backup.md) | HTTPS deployment, persistence and recovery |
| 8 | [08_final_qa.md](08_final_qa.md) | Final order journey and launch checks |

Existing filenames are retained so older links still work. Phase 5 is now a usability pass; Phase 6's bulk page is a contact page, without a quote-management system.

## Before development and launch

Development needs Java 21 and Docker. Keep the Maven Wrapper in the project.

Before accepting real orders, supply the business phone/WhatsApp and bKash details, final product prices and photos, product measurements/materials, making and courier durations, reply hours, and business policy wording. Trial prices stay in draft until checked. A missing product can remain draft while other complete products launch.

Deployment needs a chosen server, SSH access, domain/DNS access and a private off-server backup destination. Telegram credentials are optional. Do not request these again if they are already available and authorized.

## Defaults already decided

- One product type per order; quantity 1–10. Different engraving names go in one multiline field.
- No customer account, cart or online payment collection.
- Advance is 50% of the total including delivery, rounded up to whole taka, after design approval.
- Delivery is charged once per order using the selected area.
- One admin account; bulk enquiries use WhatsApp/phone.
- Tracking needs code plus full phone number, not just the last four digits.
- Making and delivery durations are shown separately; no automated production calendar.

Continue with these defaults unless the owner changes them. They do not require a separate confirmation step.

## Version and hosting references

Choose and pin a maintained Spring Boot release compatible with Java 21 using [Spring's requirements](https://docs.spring.io/spring-boot/system-requirements.html). Use current PostgreSQL 16 maintenance updates according to the [PostgreSQL version policy](https://www.postgresql.org/support/versioning/). Caddy's HTTPS setup is documented in its [official guide](https://caddyserver.com/docs/automatic-https).
