# Phase 6: tracking, help, contact and basic SEO

Follow `00_project_rules.md`. Keep public help pages short and practical.

## Tracking

`GET /track` shows two labelled fields: order code and the full phone number used for the order. `POST /track` normalizes both, accepting Bangla digits and standard BD phone prefixes. Do not use just the last four digits.

A match shows Bangla status, relevant dates and courier/tracking details. Do not show customer name, phone, address, engraving, payment references or internal notes. Do not display private order-event notes as a public timeline.

Wrong code and wrong phone give the same generic response. Use configurable limits: initially 10 attempts/15 minutes/IP and 5 failed attempts/hour/code. Counter storage must be bounded and expiring. Results use `no-store` and `noindex`; no lookup values in URLs. Offer a business contact link on errors.

## Bulk contact and information

- `/bulk`: explain that larger or corporate orders need a quote, suggest sharing product/quantity/deadline, and provide a WhatsApp link and business phone. No submission form, table or quote pipeline.
- `/faq`: a few real answers about engraving, design approval, advance, delivery and corrections.
- `/about`: concise business introduction and contact details. Owner/workshop photos are optional and must be real.
- `/policies/delivery`: charges, coverage, making/courier durations and when making starts.
- `/policies/returns`: only owner-confirmed revision, cancellation, damage and refund terms. Identify unfinished wording in the build report before launch.
- `/policies/privacy`: what order data is stored, why it is needed, who handles it for fulfilment/payment and how to contact the owner about correction/deletion. Describe the actual system and its backup retention; do not promise instant deletion from backups.

Use templates for these pages; no CMS. Keep links in the footer and contextually beside the form where useful.

## Basic SEO and performance

Provide a useful Bangla title/description, canonical URL and share image where a real image exists. Generate a sitemap from active products and public information pages. Exclude private/admin/confirmation/tracking routes; robots exclusions do not replace authorization.

Keep images appropriately sized, lazy-load images below the first screen and cache versioned static assets. Personalized responses must remain uncached. Structured-data generators, ratings markup and social analytics are outside this version.

Run a mobile Lighthouse check on home and a product page if the browser is available. Report actual results and fix obvious loading/accessibility problems. Do not claim scores without running the check or redesign the site just to chase a number.

## Done when

- Tests show that tracking requires both correct values, normalizes phones, reveals no private fields/notes and enforces limits.
- Wrong-code and wrong-phone responses match.
- Bulk contact links work, help/policy links are reachable and no invented business terms are published.
- Sitemap excludes drafts/private pages and public page titles/descriptions are meaningful.
