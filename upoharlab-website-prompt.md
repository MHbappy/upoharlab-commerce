# Build prompt: Upohar Lab — a simple, complete ordering website

Build a production-ready website for **Upohar Lab (উপহার ল্যাব)**, a small personalized-gift business in Bangladesh. Use **Spring Boot, Thymeleaf and PostgreSQL**.

Keep it easy to understand and easy to maintain. The website should feel like a complete small shop: useful product information, comfortable spacing, an easy order form and a practical admin panel. A minimal design does not mean tiny text, an empty landing page or hiding important information.

## 1. Scope and working approach

- Build one server-rendered application. Focus on helping a customer choose a product, send an order and understand what happens next.
- Customers do not need an account. They order one product type at a time, with a quantity. A keyring pair is its own product.
- The owner manages products, orders, payments and business settings through one admin account.
- Use the phase files in `prompts/upoharlab/` for implementation details. Read `00_project_rules.md` with each phase; it is the shared source of truth. This file is the overall brief.
- If asked for the whole website, work through all phases and give short progress reports. If asked for one phase, complete only that phase.
- Make routine implementation decisions yourself. Ask only for missing business information or access that actually blocks the next step. Keep unknown products in draft while continuing other work.
- Do not expand the scope to make the website look more advanced. Complete the core order flow first.

## 2. Business and order process

**Brand:** Upohar Lab · **Domain:** `upoharlab.com` · **Tagline:** আপনার নাম, যত্নে খোদাই করা

Launch products:

1. Name-engraved keyring, single.
2. Two-keyring gift pair.
3. Door name plate, 5 × 10 inch, indoor only.
4. Office desk name plate, indoor only.

Customer journey:

**Browse → choose a product → enter engraving and delivery details → send order → receive design on WhatsApp → approve and pay 50% advance → making and delivery.**

No payment is collected on the website. The owner checks payments manually. The remaining amount is due on delivery.

Existing trial values are pair ৳399, door plate ৳899, delivery inside Dhaka ৳60 and outside Dhaka ৳110. These are editable draft values, not final public prices. Do not invent prices for the other products. Publish only products whose details and prices the owner has supplied and checked.

Customer pages use natural Bangla. Admin pages may use English. Display prices and quantities in Bangla digits; accept both Bangla and English digits in forms. Keep order codes easy to copy.

## 3. Technology

- Java 21, a maintained stable Spring Boot release compatible with Java 21, and Maven Wrapper. Pin the chosen release; do not use preview versions.
- Spring MVC, Thymeleaf fragments, Spring Data JPA, Flyway and PostgreSQL 16 with current maintenance updates.
- Spring Security for admin login and CSRF protection; Bean Validation for forms.
- Plain CSS and a small amount of vanilla JavaScript for useful enhancements. Normal HTML forms must work without JavaScript.
- One application, one database, one persistent uploads directory. Use Docker Compose and Caddy for deployment with HTTPS.
- Use Spring Boot's compatible test dependencies and Testcontainers for important PostgreSQL behaviour.

## 4. Pages and visual design

**Customer pages:** home with products, product details with order form, order confirmation, tracking, FAQ, about/contact, short delivery/returns/privacy pages, and a bulk-order contact page.

**Admin pages:** login, small dashboard, orders list/detail, products list/editor and settings.

Use a warm cream background, dark readable text and a restrained terracotta accent. Use one readable Bangla font. Buttons should be easy to tap, labels visible and keyboard focus clear.

Design mobile first, but use desktop space well: a product grid on home and a photo/details split on wider product pages. Keep long text and forms comfortably narrow. Do not force every desktop page into a narrow mobile column. No popups, autoplay, decorative animation or large empty hero sections.

## 5. The product page and order form

Show these in a sensible order:

1. Real photos, product name, price and delivery charges.
2. A short description, what is included, size/material and making/delivery information.
3. One clear “অর্ডার করুন” link leading to the form on the same page.
4. The order form and visible price summary.
5. A few useful questions, care information and a WhatsApp help link.

Keep the form close to the essential product details. Use three labelled groups on one page:

| Group | Fields |
|---|---|
| Your product | Quantity, engraving text in one multiline field |
| Contact | Name, phone number |
| Delivery | Inside/outside Dhaka, full address, optional note |

Provide a short engraving example appropriate to the product. For a pair or multiple items, customers can write different names on separate lines. The actual design and spelling are confirmed on WhatsApp before making.

Default quantity is 1, maximum 10. Larger orders use the bulk contact page. Do not ask for an email, password, engraving font or payment screenshot.

Show **product subtotal + delivery = total**, followed by **50% advance after design approval**. Calculate the advance from the total including delivery, rounded up to whole taka. Never label it “pay now”.

Button: **“অর্ডার পাঠান”**.

Supporting text: **“এখন টাকা দিতে হবে না। আগে WhatsApp-এ ডিজাইন দেখাব। আপনি সম্মতি দিলে ৫০% অগ্রিম দেবেন।”**

Preserve all entered values when validation fails. Small JavaScript can refresh the total; without JavaScript, an “হিসাব আপডেট করুন” button refreshes the same form. Always show a current total before saving, and ask the customer to resubmit if the displayed price has changed.

On success, show the order code, saved total, advance due after approval, next steps, tracking link and a WhatsApp button. Do not show payment instructions that encourage paying before approval.

## 6. Small, practical admin panel

- Dashboard: new orders, orders being made and shipped orders awaiting delivery.
- Products: edit photos, price, essential details, making time and `DRAFT / ACTIVE / PAUSED` status. Simple image reorder buttons are enough.
- Orders: search/filter, see engraving and delivery details, record design approval, verify advance/balance payments, add courier information and update status.
- Statuses: `NEW → DESIGN_SENT → CONFIRMED → MAKING → SHIPPED → DELIVERED`, plus `CANCELLED`. Confirmation requires both design approval and sufficient verified advance.
- Settings: business contacts, bKash number, delivery charges, courier times, reply hours and a temporary pause on new orders.
- Keep a short order history. Use WhatsApp links for manual communication. Telegram alerts are optional when credentials are available.

## 7. Essentials that must work

Server-calculated prices, saved price snapshots, duplicate-submit protection, secure admin login, private order tracking, validated image uploads and persistent storage are part of the core build.

Tracking uses the **order code and full order phone number**, submitted privately with POST. Results show only status, relevant dates and courier details. Customer details must not leak through URLs, logs, notifications or other customers' confirmation pages.

Show making and courier durations separately. Production time starts after design approval and advance payment. Use clear estimates, not an automatic guaranteed arrival date.

Trust comes from real photos, clear prices, contact details and an understandable process. Do not invent reviews, sales counts, stock levels, guarantees or business policies.

## 8. Deployment is part of delivery

Provide a Dockerfile, Compose configuration, Caddyfile, `.env.example`, persistent database/uploads storage, backup/restore scripts and a short deployment guide.

Use one VPS deployment with HTTPS. Verify orders and images survive a restart. Back up both PostgreSQL and uploads, retain 14 daily backups and test a restore in a separate environment. Document updates and rollback.

When deployment is requested and server/domain access is already available and authorized, deploy and verify the live site. If access is missing, finish everything that can be prepared and list the exact missing access. Clearly distinguish **prepared for deployment** from **deployed and verified**.

## 9. Keep these for later

Do not build a cart, customer accounts, payment gateway, live engraving preview, font manager, product-option engine, paid add-ons, offer engine, countdowns, reviews system, capacity scheduler, staff roles, analytics dashboard, CSV exports, bulk CRM, webhook platform or CI/CD server in this version.

These are future options, not hidden requirements or reasons to delay launch. Do not create unused tables or placeholder admin menus for them.

## 10. Acceptance

A customer can understand a product, submit a correctly priced order on a phone, see what happens next and track it. The owner can fulfil that order from the admin panel. Forms work without JavaScript. The layout is readable on phone and desktop. The app can be deployed with HTTPS and recover its data from a tested backup.

Report what was built, what was checked and any remaining blockers. Never describe an untested deployment or unavailable live server as complete.
