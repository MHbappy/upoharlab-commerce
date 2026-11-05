-- Backstops matching the limits the forms already enforce: price up to 1,000,000 and delivery up
-- to 10,000, so the largest order is 10 x 1,000,000 + 10,000. A mistyped figure cannot be saved.
ALTER TABLE product ADD CONSTRAINT product_price_limit CHECK (price <= 1000000);
ALTER TABLE orders
    ADD CONSTRAINT orders_unit_price_limit CHECK (unit_price_snapshot <= 1000000),
    ADD CONSTRAINT orders_delivery_limit CHECK (delivery_charge_snapshot <= 10000),
    ADD CONSTRAINT orders_total_limit CHECK (total <= 10010000);
ALTER TABLE payment ADD CONSTRAINT payment_amount_limit CHECK (amount <= 10010000);

CREATE INDEX product_image_product_idx ON product_image(product_id);
