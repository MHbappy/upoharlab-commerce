-- Products can take no customer text, so not every order has a design to approve.
-- Earlier orders all did; orders without one can be confirmed straight from NEW.
ALTER TABLE orders ADD COLUMN needs_design boolean NOT NULL DEFAULT true;
