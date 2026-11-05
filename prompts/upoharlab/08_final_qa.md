# Phase 8: final checks

Follow `00_project_rules.md`. Fix defects in the agreed scope; do not add features.

Record each item as **pass**, **fail** or **not checked**, with short evidence. A missing real device or server check stays “not checked”.

## Customer journey

- [ ] Home and product pages explain the product, price, delivery and design-before-payment process clearly.
- [ ] A mobile customer can submit quantity, engraving, name, phone, area and address without an account.
- [ ] Pair quantities and multiple engraving names are understandable.
- [ ] Errors preserve inputs and explain the fix; a paused shop/product rejects new orders clearly.
- [ ] Subtotal, delivery, total and advance match the server and saved admin order.
- [ ] Changed prices receive a fresh review before saving; repeat clicks create only one order.
- [ ] Ordering and summary updates work without JavaScript.
- [ ] Confirmation shows the code and next steps, with no instruction to pay before design approval.
- [ ] Tracking works using code plus full phone number and exposes no personal data or internal notes.

## Owner workflow

- [ ] Login, product editing/photos, publishing/pausing and settings work.
- [ ] The owner can find an order, record approval/advance, make it, add courier details and mark it delivered.
- [ ] Invalid transitions, duplicate transaction references, overpayments and excessive refunds are rejected.
- [ ] Payment balances/history are correct; delivered does not automatically mean paid.
- [ ] Telegram, if configured, works; without it the order still succeeds and is visible in admin.

## Privacy and layout

- [ ] Admin protection, CSRF, rate limits and session-specific confirmation access work.
- [ ] No personal form data or secrets leak into URLs, logs, notifications or another session's pages.
- [ ] Private/form responses containing personal data are not cached.
- [ ] Phone and desktop layouts are comfortable; no horizontal scroll at 320px or obstructed controls.
- [ ] Bangla text, keyboard focus, labels, errors, contrast and tap targets are usable.
- [ ] Photos and claims are real; no fake counts, reviews, urgency or unconfirmed promises.

## Deployment and business readiness

- [ ] Real prices, contacts, making/delivery times and policy text are checked; unfinished products stay draft.
- [ ] HTTPS and domain redirects work; database and health endpoints are not publicly reachable.
- [ ] Orders and images survive a restart.
- [ ] A database/uploads backup and a separate restore have both succeeded; the daily off-server schedule is configured.
- [ ] Startup, updates and rollback are documented without secrets.

## Final report

State what works, what was tested and any remaining blockers. Distinguish development complete, deployment prepared, live deployed and live verified. Do not call the site ready to accept orders while an essential ordering, privacy, business-data or recovery check is failing or unverified.
