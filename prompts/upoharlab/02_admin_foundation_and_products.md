# Phase 2: admin login, settings and product pages

Follow `00_project_rules.md`. Build the shop and the owner's basic editing tools. The working order form follows in Phase 3; do not leave a non-functional order button in the finished website.

## Admin foundation

- Bootstrap one admin from environment credentials only when no account exists. Require credentials on that first boot; later restarts must not reset the password.
- Form login, BCrypt, CSRF, POST logout, an 8-hour session timeout and generic login errors. Rate-limit by client IP and username. Production session cookies are secure and HttpOnly.
- Navigation has only dashboard, orders, products, settings and logout. No roles, user manager or two-factor setup in this version.
- Settings edit the non-secret values listed in Phase 1 with clear labels and validation. Unknown details remain empty. Enabling `accepting_orders` requires contact/payment details, checked delivery charges and complete courier/reply durations; inactive products may stay draft.

## Product editor and images

Use one straightforward form with sections for basic information, price/details, photos and publishing. `ACTIVE` requires a title, unique slug, positive price, real photo with alt text, short description, what is included, size/material, engraving guidance and a valid making-day range. Keep other copy optional. Explain missing fields beside the fields; no completeness meter or mandatory four-FAQ rule.

The owner can add JPEG/PNG photos, remove photos and reorder them with up/down buttons. Check decoded image content, allow at most 5 MB and 20 megapixels, re-encode to JPEG at sensible display/thumbnail sizes and strip metadata. Use random filenames under `/data/uploads`; reject paths from the client. No SVG or arbitrary file uploads.

Preserve a product's indoor-only warning. Products used in orders can be paused, not deleted. Changes to prices or titles must not change old order snapshots.

## Home page

Short introduction → active product grid → four order steps → brief help/contact section. Show real photos, titles and prices on cards. Do not add category filters or search for four products. If no products are ready, show an honest availability/contact message instead of demo products.

## Product page

- `ACTIVE`: details and ordering. `PAUSED`: details remain visible with a clear pause message. `DRAFT` and unknown slugs: public 404; admin preview requires login.
- On desktop, place photos beside the product summary; on phones, stack them. One main photo and small thumbnails are enough. No carousel library.
- Show title, short description, price and both delivery charges prominently. Explain that quantity for a pair counts pairs.
- Follow with what is included, size/material, engraving guidance and making/courier durations. Keep the order form close to these essentials.
- Use **“অর্ডার করুন”** as the link to the same-page form. When the product or shop is paused, replace ordering with the pause explanation and a contact link.
- Care details and short common questions come after the form. Hide missing optional content.
- State that making starts after approval and advance. Once an area is selected, show its courier range. Do not calculate a promised arrival date from today's date.

## Done when

- The owner can log in, change settings, upload/reorder images and publish or pause a complete product.
- Private routes and draft previews require login; CSRF and login limits work.
- Invalid images and incomplete active products are rejected with useful messages.
- Public product pages contain real details and no empty blocks, and work at phone and desktop widths.
- The full ordering path is clearly listed as pending Phase 3, not reported as finished.
