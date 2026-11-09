-- Switch every order to COD without changing totals, payment history or refunds.
ALTER TABLE orders DROP CONSTRAINT orders_check;
ALTER TABLE orders DROP CONSTRAINT orders_check2;
UPDATE orders SET advance_required = 0;
ALTER TABLE orders ADD CONSTRAINT orders_cod_no_advance CHECK (advance_required = 0);

-- Customers may provide personalization details after placing a guest order.
ALTER TABLE orders DROP CONSTRAINT orders_engraving_text_check;
ALTER TABLE orders ADD CONSTRAINT orders_engraving_length CHECK (char_length(engraving_text) <= 1000);
