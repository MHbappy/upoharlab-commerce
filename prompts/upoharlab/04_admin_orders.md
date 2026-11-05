# Phase 4: daily order management

Follow `00_project_rules.md`. Keep this focused on fulfilling orders; do not build a management dashboard with charts or staff permissions.

## Dashboard, list and detail

Dashboard: three counts — new orders, orders being made, shipped orders awaiting delivery — and a recent-orders list.

The orders list supports status filtering, search by code or phone and pagination. Submit phone searches with POST, preserve the search across pagination without putting it in URLs, and mask phones in lists. Keep the full phone on the authenticated detail page.

Order detail shows code, engraving text, quantity, customer contact/address, optional note, saved price breakdown, approval, verified payments, remaining balance, courier/tracking and a short chronological history. Keep internal notes separate from the customer's note.

Allow correction of contact/address before shipping; record that a correction happened without copying old/new personal data into event logs. Do not silently change quantity, product or engraving after confirmation. Handle those changes by cancelling and taking an agreed replacement order in this version.

## Statuses

Enforce these transitions on the server and record actor/time in `order_event`:

| From | To | Condition |
|---|---|---|
| `NEW` | `DESIGN_SENT` | Owner has sent the design on WhatsApp |
| `DESIGN_SENT` | `CONFIRMED` | Written approval recorded and net verified payments cover the required advance |
| `CONFIRMED` | `MAKING` | Approval and advance still valid |
| `MAKING` | `SHIPPED` | Courier name and tracking/reference entered |
| `SHIPPED` | `DELIVERED` | Owner confirms delivery; set delivered_at |
| Any status before `SHIPPED` | `CANCELLED` | Reason entered; show payments/refunds for follow-up |

Approval is a simple “Design approved on WhatsApp” action recording the time. There is no file-upload approval portal. If approval arrives before payment, the order remains `DESIGN_SENT` and the detail page clearly shows what is missing.

Delivery does not automatically mark the balance paid. A delivered order can still show money awaiting courier settlement. Cancelled orders show net money held and refund history instead of a misleading “balance due”; the owner applies the agreed cancellation terms. Post-delivery issues use notes and verified refunds, without another returns workflow.

## Payment records

- The owner checks their bKash account or actual cash/courier settlement before adding a payment. A screenshot alone is not verification.
- Record amount, method, transaction reference where applicable, actor and time. A bKash record requires its actual reference, unique when present.
- `net_paid = sum(PAYMENT) - sum(REFUND)`; for non-cancelled orders, `balance = total - net_paid`.
- Require positive amounts, prevent overpayment and refunds exceeding net paid. Validate and save under an order-row lock so concurrent actions cannot double-record money or bypass transition conditions.
- Record refunds as separate rows; do not delete payment history. Refund processing occurs outside the website, then the owner records the verified refund.
- No online payment collection, reconciliation engine or automatic refunds.

Provide a simple WhatsApp contact button and, if helpful, prefilled design/payment/shipping messages. URL-encode text and use the customer's normalized `880` number. Nothing sends automatically; `Referrer-Policy: no-referrer` applies.

## Done when

- An owner can take a test order from new through delivery, record advance/balance and see the correct history.
- Tests reject invalid transitions, missing approval/advance, missing courier details, duplicate transaction references, overpayment and excessive refunds.
- Cancellation/refund balances are understandable and delivered does not imply paid.
- Concurrent payment/status actions preserve money and status rules.
- All actions require login and CSRF; list/detail privacy rules hold.
