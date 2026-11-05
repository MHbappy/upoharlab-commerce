-- A paused product stays publicly visible, so it needs the same essentials as an active one.
ALTER TABLE product ADD CONSTRAINT product_paused_complete CHECK (
    status <> 'PAUSED'
    OR (price IS NOT NULL AND making_days_min IS NOT NULL AND making_days_max IS NOT NULL)
);

-- Explains what a quantity of 1 means, without tying that text to a particular product URL.
ALTER TABLE product ADD COLUMN quantity_help_bn text;
UPDATE product
SET quantity_help_bn = 'প্রতি সেটে দুটি চাবির রিং পাবেন। পরিমাণ ১ মানে এক জোড়া।'
WHERE slug = 'keyring-gift-pair';
