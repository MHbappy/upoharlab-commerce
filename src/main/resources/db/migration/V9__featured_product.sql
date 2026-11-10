-- The owner can feature one product at the top of the home page.
ALTER TABLE product ADD COLUMN featured boolean NOT NULL DEFAULT false;
-- At most one featured product.
CREATE UNIQUE INDEX product_one_featured ON product ((featured)) WHERE featured;
