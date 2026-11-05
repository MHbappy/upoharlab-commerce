# Phase 3: a clear, reliable order flow

Follow `00_project_rules.md`. This is the core customer journey. Do not add options, live engraving previews, offers or production scheduling.

## 1. Form and price summary

One form on the product page, with three visible groups:

| Group | Fields |
|---|---|
| Product | Quantity (1–10, default 1), required engraving textarea |
| Contact | Required name and phone |
| Delivery | Required inside/outside Dhaka choice, full address, optional note |

Do not preselect the delivery area. Before selection, show both delivery rates and label the total as awaiting an area choice. Quantity is per product unit; one gift-pair unit contains two keyrings.

The engraving field accepts multiple lines so each item can have different text. Give a product-specific example and explain how to label names for multiple items. Use practical input bounds: name 100, engraving 1000, address 500 and note 500 Unicode code points after NFC normalization. Show product engraving constraints in guidance; the owner confirms the actual layout/spelling on WhatsApp. Do not build a dynamic field generator or reject Bengali text with an ASCII-only rule.

Use `type="tel"`, useful autocomplete attributes, visible labels, inline Bangla errors and a focusable error summary. Preserve all inputs after errors. Keep the optional note at the end. Explain the link to bulk contact for more than 10 units.

One `PricingService`, in integer taka:

```text
subtotal = current product price × quantity
delivery = current charge for selected area, once per order
total    = subtotal + delivery
advance  = (total + 1) / 2 using integer division
balance  = total - advance
```

Show each line, with **“ডিজাইনে সম্মতির পরে ৫০% অগ্রিম”** and the remaining balance. The submit button is **“অর্ডার পাঠান”**. Beneath it: **“এখন টাকা দিতে হবে না। আগে WhatsApp-এ ডিজাইন দেখাব। আপনি সম্মতি দিলে ৫০% অগ্রিম দেবেন।”**

## 2. JavaScript is an enhancement

Small vanilla JavaScript can update the visible price summary as quantity/area changes. Use the same pricing inputs and rounding as the server; the server remains authoritative.

Without JavaScript, **“হিসাব আপডেট করুন”** posts the same form with `action=quote` and re-renders it with all values intact. This action validates only quantity/area and never saves an order. `action=submit` attempts the order. Neither path puts personal data in the URL.

Before saving, ensure the customer has seen the current breakdown for their selected quantity and area. Keep a session-bound quote version for product/delivery prices; never use the browser's total as the amount to charge. If the summary is missing/stale or price settings changed, re-render with the current total and **“মোট হিসাব দেখে আবার অর্ডার পাঠান।”** Do not save yet. The next unchanged submit can save. This also covers the no-JavaScript path without a separate checkout page.

## 3. Save once, then confirm

1. Validate CSRF, session-bound submit token, product `ACTIVE`, shop accepting orders, fields and pricing. Refresh the form with a clear message if ordering has been paused.
2. In one transaction, generate the order code, save `NEW`, snapshot product title/unit price/delivery/total/advance, and write an initial order event.
3. Enforce a unique submit token in PostgreSQL. Repeating the same successful request in the same session redirects to the same confirmation, including under concurrent clicks; check this before issuing a new order. Never authorize another session based only on a submitted token.
4. Grant the submitting session access to that order and redirect to `/order/{code}/success`. A guessed code from another session must reveal no order data. After session expiry, the customer uses tracking.
5. If Telegram is configured, send one alert after commit with only order code, product, quantity, total and admin link. Escape values; a notification failure must not lose the order or trigger a duplicate. No email/webhook integration.

Use a bounded in-memory rate limiter, initially 5 new submissions/hour/IP and configurable, plus a honeypot excluded from keyboard and screen-reader navigation. Quote refreshes and successful duplicate retries do not consume new-order allowance. A triggered honeypot saves nothing; it must not receive a real order code or create a fake order record. Rate-limit errors explain how to contact the business.

## 4. Order codes

Use `UL-YYYYMM-NNN` with the month in `Asia/Dhaka`. Increment `order_code_counter` atomically with a PostgreSQL upsert returning the counter value, in the order transaction. Pad to at least three digits and allow larger numbers. Do not generate codes with `count(*) + 1`.

## 5. Confirmation

Show **“আপনার অর্ডার পেয়েছি”**, copyable code, saved total, advance after approval and a short list of next steps:

1. The owner will contact you on WhatsApp within the configured reply time.
2. Check the design and spelling and give written approval.
3. Follow the owner's bKash instructions for the advance.
4. Making starts; courier details follow after shipping.

Add tracking and business WhatsApp links; WhatsApp text contains only the order code. Do not put the customer's phone/address or a “pay now” instruction here. Send `no-store`, `noindex` and `Referrer-Policy: no-referrer`. Copying the code is optional JavaScript; the text is selectable without it.

## Done when

Focused tests cover pricing/rounding, Bangla phones, invalid input, paused products/shop, stale prices, price tampering, immutable snapshots, parallel code generation, duplicate clicks, session isolation and notification failure. Use PostgreSQL tests for database constraints and concurrency.

Manually complete an order with and without JavaScript. Verify input preservation, summary updates and the extra review when prices change. Confirmation and the admin order must show the same saved totals. No personal data appears in URLs or notifications.
