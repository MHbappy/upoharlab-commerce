-- Local test data only. Safe to run repeatedly. Never add this to db/migration.
-- Only fills empty settings and untouched products, so your own edits are kept.
BEGIN;

UPDATE setting s SET value = d.value
FROM (VALUES
  ('phone', '01700000000'),
  ('whatsapp', '01700000000'),
  ('bkash_number', '01700000000'),
  ('business_email', 'demo@example.com'),
  ('courier_days_inside_min', '1'),
  ('courier_days_inside_max', '2'),
  ('courier_days_outside_min', '2'),
  ('courier_days_outside_max', '4'),
  ('reply_hours', '6')
) AS d(key, value)
WHERE s.key = d.key AND s.value = '';
UPDATE setting SET value = 'true' WHERE key = 'accepting_orders';

CREATE TEMP TABLE demo_product (
  slug text, price int, short_d text, description text, includes text, material text,
  size text, help text, care text, days_min int, days_max int, status text, photos text[]
) ON COMMIT DROP;
INSERT INTO demo_product VALUES
('engraved-keyring', 199, 'নমুনা: একটি নাম খোদাই করা কাঠের চাবির রিং।',
 'এটি পরীক্ষার জন্য নমুনা তথ্য।', 'একটি চাবির রিং', 'কাঠ', '৬ × ৩ সেমি',
 'বাংলা বা ইংরেজিতে একটি নাম, সর্বোচ্চ ১২ অক্ষর।', 'শুকনো কাপড়ে মুছুন।', 2, 3, 'ACTIVE',
 ARRAY['d0000000-0000-4000-8000-000000000001.jpg']),
('keyring-gift-pair', 399, 'নমুনা: প্রিয়জনের সঙ্গে মিলিয়ে দুটি চাবির রিং।',
 'এটি পরীক্ষার জন্য নমুনা তথ্য।', 'দুটি চাবির রিং, একটি উপহারের বাক্স', 'কাঠ', 'প্রতিটি ৬ × ৩ সেমি',
 'প্রতিটিতে একটি নাম, সর্বোচ্চ ১২ অক্ষর।', 'শুকনো কাপড়ে মুছুন।', 2, 4, 'ACTIVE',
 ARRAY['d0000000-0000-4000-8000-000000000002.jpg', 'd0000000-0000-4000-8000-000000000003.jpg']),
('door-name-plate', 899, 'নমুনা: বাসার দরজার জন্য নাম খোদাই করা প্লেট।',
 'এটি পরীক্ষার জন্য নমুনা তথ্য।', 'একটি নেমপ্লেট, লাগানোর স্ক্রু', 'কাঠ', '৫ × ১০ ইঞ্চি',
 'পরিবারের নাম বা বাসার নাম, সর্বোচ্চ ২০ অক্ষর।', 'পানি থেকে দূরে রাখুন।', 3, 5, 'ACTIVE',
 ARRAY['d0000000-0000-4000-8000-000000000004.jpg']),
('desk-name-plate', 699, 'নমুনা: অফিসের টেবিলের জন্য নাম ও পদবির প্লেট।',
 'এটি পরীক্ষার জন্য নমুনা তথ্য। পণ্যটি বন্ধ থাকলে পাতা কেমন দেখায়, তা দেখাতে এটি স্থগিত রাখা হয়েছে।',
 'একটি ডেস্ক নেমপ্লেট', 'কাঠ', '৮ × ২ ইঞ্চি', 'নাম ও পদবি, সর্বোচ্চ ৩০ অক্ষর।',
 'শুকনো কাপড়ে মুছুন।', 3, 5, 'PAUSED',
 ARRAY['d0000000-0000-4000-8000-000000000005.jpg']);

INSERT INTO product_image (product_id, filename, alt_bn, sort_order)
SELECT p.id, f.filename, 'পরীক্ষার নমুনা ছবি', f.position - 1
FROM demo_product d
JOIN product p ON p.slug = d.slug
CROSS JOIN LATERAL unnest(d.photos) WITH ORDINALITY AS f(filename, position)
WHERE coalesce(p.short_description_bn, '') = ''
ON CONFLICT (filename) DO NOTHING;

UPDATE product p SET
  price = coalesce(p.price, d.price),
  short_description_bn = d.short_d,
  description_bn = coalesce(nullif(p.description_bn, ''), d.description),
  includes_bn = coalesce(nullif(p.includes_bn, ''), d.includes),
  material_bn = coalesce(nullif(p.material_bn, ''), d.material),
  size_bn = coalesce(nullif(p.size_bn, ''), d.size),
  engraving_help_bn = coalesce(nullif(p.engraving_help_bn, ''), d.help),
  care_bn = coalesce(nullif(p.care_bn, ''), d.care),
  making_days_min = coalesce(p.making_days_min, d.days_min),
  making_days_max = coalesce(p.making_days_max, d.days_max),
  status = d.status,
  updated_at = now()
FROM demo_product d
WHERE p.slug = d.slug AND coalesce(p.short_description_bn, '') = '';

-- Replace earlier demo orders, so running this again gives a clean set.
DELETE FROM order_event WHERE order_id IN (SELECT id FROM orders WHERE submit_token LIKE 'demo-%');
DELETE FROM payment WHERE order_id IN (SELECT id FROM orders WHERE submit_token LIKE 'demo-%');
DELETE FROM orders WHERE submit_token LIKE 'demo-%';

CREATE FUNCTION pg_temp.demo_order(
  n int, product_slug text, qty int, engraving text, customer text, customer_phone text,
  delivery_area text, customer_address text, customer_note text, final_status text,
  age_days int, paid int, payment_method text, refund int, reason text, internal_note text
) RETURNS void AS $$
DECLARE
  p product%ROWTYPE;
  charge int;
  order_total int;
  new_id bigint;
  admin_id bigint;
  admin_name text;
  order_code text;
  placed timestamptz := now() - make_interval(days => age_days);
  path text[] := ARRAY['NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED'];
  reached int;
  step int;
BEGIN
  SELECT * INTO p FROM product WHERE slug = product_slug;
  SELECT value::int INTO charge FROM setting
    WHERE key = CASE delivery_area WHEN 'INSIDE' THEN 'delivery_inside' ELSE 'delivery_outside' END;
  SELECT id, username INTO admin_id, admin_name FROM admin_user ORDER BY id LIMIT 1;
  order_total := p.price * qty + charge;
  order_code := 'UL-' || to_char(now() AT TIME ZONE 'Asia/Dhaka', 'YYYYMM') || '-' || (900 + n);
  reached := coalesce(array_position(path, final_status), 2);

  INSERT INTO orders (code, submit_token, product_id, product_title_snapshot, quantity,
      engraving_text, customer_name, phone, area, address, note, unit_price_snapshot,
      delivery_charge_snapshot, total, advance_required, status, design_approved_at, courier,
      tracking_no, delivered_at, created_at, updated_at)
  VALUES (order_code, 'demo-' || n, p.id, p.title_bn, qty, engraving, customer, customer_phone,
      delivery_area, customer_address, customer_note, p.price, charge, order_total,
      0, final_status,
      CASE WHEN final_status IN ('CONFIRMED','MAKING','SHIPPED','DELIVERED')
             OR internal_note = 'approved' THEN placed + interval '1 day' END,
      CASE WHEN final_status IN ('SHIPPED','DELIVERED') THEN 'Pathao' END,
      CASE WHEN final_status IN ('SHIPPED','DELIVERED') THEN 'DEMO-TRACK-' || n END,
      CASE WHEN final_status = 'DELIVERED' THEN placed + interval '5 days' END,
      placed, now())
  RETURNING id INTO new_id;

  INSERT INTO order_event (order_id, event_type, to_status, actor, created_at)
  VALUES (new_id, 'CREATED', 'NEW', 'customer', placed);
  FOR step IN 2..reached LOOP
    INSERT INTO order_event (order_id, event_type, from_status, to_status, actor, created_at)
    VALUES (new_id, 'STATUS_CHANGED', path[step - 1], path[step], admin_name,
            placed + make_interval(hours => step * 6));
  END LOOP;
  IF final_status = 'CANCELLED' THEN
    INSERT INTO order_event (order_id, event_type, from_status, to_status, note, actor, created_at)
    VALUES (new_id, 'STATUS_CHANGED', 'DESIGN_SENT', 'CANCELLED', reason, admin_name,
            placed + interval '2 days');
  END IF;
  IF paid > 0 THEN
    INSERT INTO payment (order_id, kind, method, amount, transaction_ref, recorded_by, recorded_at)
    VALUES (new_id, 'PAYMENT', payment_method, paid,
            CASE WHEN payment_method = 'BKASH' THEN 'DEMOTRX' || n END, admin_id,
            placed + interval '1 day');
    INSERT INTO order_event (order_id, event_type, note, actor, created_at)
    VALUES (new_id, 'PAYMENT_RECORDED', '৳' || paid || ' ' || payment_method, admin_name,
            placed + interval '1 day');
  END IF;
  IF refund > 0 THEN
    INSERT INTO payment (order_id, kind, method, amount, transaction_ref, recorded_by, recorded_at)
    VALUES (new_id, 'REFUND', 'BKASH', refund, 'DEMOREF' || n, admin_id, placed + interval '3 days');
    INSERT INTO order_event (order_id, event_type, note, actor, created_at)
    VALUES (new_id, 'REFUND_RECORDED', '৳' || refund || ' BKASH', admin_name,
            placed + interval '3 days');
  END IF;
  IF internal_note IS NOT NULL AND internal_note <> 'approved' THEN
    INSERT INTO order_event (order_id, event_type, note, actor, created_at)
    VALUES (new_id, 'NOTE', internal_note, admin_name, placed + interval '1 hour');
  END IF;
END;
$$ LANGUAGE plpgsql;

-- One worked example for every order state. Numbers and addresses are made up.
SELECT pg_temp.demo_order(1, 'keyring-gift-pair', 1, E'১ম: রাফি\n২য়: ক্ষমা', 'নমুনা রহিম', '01700000001', 'INSIDE', 'নমুনা ঠিকানা, মিরপুর ১০, ঢাকা', 'জন্মদিনের উপহার', 'NEW', 0, 0, NULL, 0, NULL, NULL);
SELECT pg_temp.demo_order(2, 'engraved-keyring', 3, E'আয়েশা\nসুমাইয়া\nনুসরাত', 'নমুনা করিম', '01700000002', 'OUTSIDE', 'নমুনা ঠিকানা, জিন্দাবাজার, সিলেট', NULL, 'NEW', 0, 0, NULL, 0, NULL, 'গ্রাহক সন্ধ্যায় কল করতে বলেছেন');
SELECT pg_temp.demo_order(3, 'door-name-plate', 1, 'রহমান পরিবার', 'নমুনা সালমা', '01700000003', 'INSIDE', 'নমুনা ঠিকানা, ধানমন্ডি, ঢাকা', NULL, 'DESIGN_SENT', 1, 0, NULL, 0, NULL, NULL);
SELECT pg_temp.demo_order(4, 'keyring-gift-pair', 2, E'১ম: তানভীর\n২য়: নাদিয়া\n৩য়: সামি\n৪র্থ: রিমা', 'নমুনা জামাল', '01700000004', 'OUTSIDE', 'নমুনা ঠিকানা, আগ্রাবাদ, চট্টগ্রাম', NULL, 'DESIGN_SENT', 2, 200, 'BKASH', 0, NULL, 'approved');
SELECT pg_temp.demo_order(5, 'engraved-keyring', 1, 'Hasan', 'নমুনা হাসান', '01700000005', 'INSIDE', 'নমুনা ঠিকানা, উত্তরা, ঢাকা', NULL, 'CONFIRMED', 3, 130, 'BKASH', 0, NULL, NULL);
SELECT pg_temp.demo_order(6, 'door-name-plate', 1, 'স্বপ্ননীড়', 'নমুনা নাসরিন', '01700000006', 'OUTSIDE', 'নমুনা ঠিকানা, সাহেব বাজার, রাজশাহী', 'দরজার রং গাঢ় বাদামি', 'MAKING', 4, 505, 'BKASH', 0, NULL, 'কাঠ বদলে একই রঙের নেওয়া হয়েছে');
SELECT pg_temp.demo_order(7, 'keyring-gift-pair', 1, E'১ম: মাহি\n২য়: অর্ণব', 'নমুনা মাহি', '01700000007', 'INSIDE', 'নমুনা ঠিকানা, গুলশান, ঢাকা', NULL, 'SHIPPED', 6, 230, 'BKASH', 0, NULL, NULL);
SELECT pg_temp.demo_order(8, 'engraved-keyring', 2, E'ইমন\nসাদিয়া', 'নমুনা ইমন', '01700000008', 'OUTSIDE', 'নমুনা ঠিকানা, খুলনা সদর, খুলনা', NULL, 'DELIVERED', 9, 254, 'BKASH', 0, NULL, 'বাকি টাকা কুরিয়ার থেকে আসেনি');
SELECT pg_temp.demo_order(9, 'door-name-plate', 1, 'চৌধুরী ভিলা', 'নমুনা চৌধুরী', '01700000009', 'INSIDE', 'নমুনা ঠিকানা, বনানী, ঢাকা', NULL, 'DELIVERED', 12, 959, 'CASH', 0, NULL, NULL);
SELECT pg_temp.demo_order(10, 'engraved-keyring', 1, 'লিমা', 'নমুনা লিমা', '01700000010', 'INSIDE', 'নমুনা ঠিকানা, মোহাম্মদপুর, ঢাকা', NULL, 'CANCELLED', 7, 130, 'BKASH', 130, 'গ্রাহক ডিজাইন নিতে চাননি', NULL);
SELECT pg_temp.demo_order(11, 'keyring-gift-pair', 1, E'১ম: পিয়াল\n২য়: তিথি', 'নমুনা পিয়াল', '01700000011', 'OUTSIDE', 'নমুনা ঠিকানা, বরিশাল সদর, বরিশাল', NULL, 'CANCELLED', 8, 255, 'BKASH', 0, 'ঠিকানা বদলে নতুন অর্ডার হবে', NULL);

-- Older delivered orders, so the list has enough rows to show its pagination.
SELECT pg_temp.demo_order(11 + g, 'engraved-keyring', 1, 'নমুনা নাম ' || g, 'নমুনা গ্রাহক ' || g,
       '017000001' || lpad(g::text, 2, '0'), 'INSIDE', 'নমুনা ঠিকানা ' || g || ', ঢাকা', NULL,
       'DELIVERED', 14 + g, 259, 'CASH', 0, NULL, NULL)
FROM generate_series(1, 19) AS g;

COMMIT;

SELECT code || '  |  ' || phone || '  |  ' || status AS "code | phone | status"
FROM orders WHERE submit_token LIKE 'demo-%' AND substring(submit_token from 6)::int <= 11
ORDER BY substring(submit_token from 6)::int;
