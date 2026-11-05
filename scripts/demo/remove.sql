-- Removes the local test data. Products you edited yourself and real orders are not touched.
BEGIN;

DELETE FROM order_event WHERE order_id IN (SELECT id FROM orders WHERE submit_token LIKE 'demo-%');
DELETE FROM payment WHERE order_id IN (SELECT id FROM orders WHERE submit_token LIKE 'demo-%');
DELETE FROM orders WHERE submit_token LIKE 'demo-%';

DELETE FROM product_image WHERE filename LIKE 'd0000000-0000-4000-8000-%';

UPDATE product SET
  status = 'DRAFT',
  price = CASE slug WHEN 'keyring-gift-pair' THEN 399 WHEN 'door-name-plate' THEN 899 END,
  short_description_bn = NULL, description_bn = NULL, includes_bn = NULL, material_bn = NULL,
  size_bn = CASE slug WHEN 'door-name-plate' THEN '৫ × ১০ ইঞ্চি' END,
  engraving_help_bn = NULL, care_bn = NULL, making_days_min = NULL, making_days_max = NULL,
  updated_at = now()
WHERE short_description_bn LIKE 'নমুনা:%';

UPDATE setting SET value = '' WHERE
  (key IN ('phone', 'whatsapp', 'bkash_number') AND value = '01700000000')
  OR (key = 'business_email' AND value = 'demo@example.com')
  OR (key IN ('courier_days_inside_min', 'courier_days_outside_max') AND value IN ('1', '4'))
  OR (key IN ('courier_days_inside_max', 'courier_days_outside_min') AND value = '2')
  OR (key = 'reply_hours' AND value = '6');
UPDATE setting SET value = 'false' WHERE key = 'accepting_orders';

COMMIT;
