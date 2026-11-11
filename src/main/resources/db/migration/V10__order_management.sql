-- Parcels can come back, and mistaken status steps and money records can be corrected.
ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED','RETURNED')
);

ALTER TABLE order_event DROP CONSTRAINT order_event_from_status_check;
ALTER TABLE order_event ADD CONSTRAINT order_event_from_status_check CHECK (
    from_status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED','RETURNED')
);
ALTER TABLE order_event DROP CONSTRAINT order_event_to_status_check;
ALTER TABLE order_event ADD CONSTRAINT order_event_to_status_check CHECK (
    to_status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED','RETURNED')
);
ALTER TABLE order_event DROP CONSTRAINT order_event_event_type_check;
ALTER TABLE order_event ADD CONSTRAINT order_event_event_type_check CHECK (
    event_type IN ('CREATED','STATUS_CHANGED','DESIGN_APPROVED','PAYMENT_RECORDED','REFUND_RECORDED',
                   'CONTACT_UPDATED','NOTE','COURIER_UPDATED','STATUS_UNDONE','PAYMENT_VOIDED')
);

-- A voided record stays visible for the audit trail, but no longer counts as money or holds its reference.
ALTER TABLE payment
    ADD COLUMN voided_at timestamptz,
    ADD COLUMN voided_by varchar(200),
    ADD COLUMN void_reason varchar(500);
ALTER TABLE payment DROP CONSTRAINT payment_transaction_ref_key;
CREATE UNIQUE INDEX payment_active_reference_idx ON payment(transaction_ref) WHERE voided_at IS NULL;
