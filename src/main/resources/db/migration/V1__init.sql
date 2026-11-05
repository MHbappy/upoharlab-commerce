CREATE TABLE product (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug varchar(150) NOT NULL UNIQUE,
    title_bn varchar(200) NOT NULL,
    short_description_bn text,
    description_bn text,
    includes_bn text,
    material_bn text,
    size_bn text,
    engraving_help_bn text,
    care_bn text,
    indoor_only boolean NOT NULL DEFAULT false,
    price integer CHECK (price > 0),
    making_days_min integer CHECK (making_days_min >= 0),
    making_days_max integer CHECK (making_days_max >= making_days_min),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','PAUSED')),
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (status <> 'ACTIVE' OR (price IS NOT NULL AND making_days_min IS NOT NULL AND making_days_max IS NOT NULL))
);
CREATE TABLE product_image (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL REFERENCES product(id),
    filename varchar(255) NOT NULL UNIQUE,
    alt_bn varchar(250) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0
);
CREATE TABLE admin_user (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username varchar(100) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE orders (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(40) NOT NULL UNIQUE,
    submit_token varchar(128) NOT NULL UNIQUE,
    product_id bigint NOT NULL REFERENCES product(id),
    product_title_snapshot varchar(200) NOT NULL,
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    engraving_text text NOT NULL CHECK (char_length(engraving_text) BETWEEN 1 AND 1000),
    customer_name varchar(100) NOT NULL,
    phone varchar(11) NOT NULL CHECK (phone ~ '^01[3-9][0-9]{8}$'),
    area varchar(10) NOT NULL CHECK (area IN ('INSIDE','OUTSIDE')),
    address varchar(500) NOT NULL,
    note varchar(500),
    unit_price_snapshot integer NOT NULL CHECK (unit_price_snapshot > 0),
    delivery_charge_snapshot integer NOT NULL CHECK (delivery_charge_snapshot >= 0),
    total integer NOT NULL CHECK (total > 0),
    advance_required integer NOT NULL CHECK (advance_required > 0 AND advance_required <= total),
    status varchar(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED')),
    design_approved_at timestamptz,
    courier varchar(100),
    tracking_no varchar(150),
    delivered_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (total::bigint = unit_price_snapshot::bigint * quantity + delivery_charge_snapshot),
    CHECK (advance_required::bigint = (total::bigint + 1) / 2)
);
CREATE INDEX orders_status_idx ON orders(status);
CREATE INDEX orders_created_at_idx ON orders(created_at);
CREATE INDEX orders_phone_idx ON orders(phone);
CREATE TABLE payment (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL REFERENCES orders(id),
    kind varchar(10) NOT NULL CHECK (kind IN ('PAYMENT','REFUND')),
    method varchar(10) NOT NULL CHECK (method IN ('BKASH','CASH','COD')),
    amount integer NOT NULL CHECK (amount > 0),
    transaction_ref varchar(100) UNIQUE,
    recorded_by bigint NOT NULL REFERENCES admin_user(id),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    CHECK (transaction_ref IS NULL OR length(trim(transaction_ref)) > 0),
    CHECK (method <> 'BKASH' OR transaction_ref IS NOT NULL)
);
CREATE INDEX payment_order_idx ON payment(order_id);
CREATE TABLE order_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id bigint NOT NULL REFERENCES orders(id),
    event_type varchar(30) NOT NULL CHECK (event_type IN ('CREATED','STATUS_CHANGED','DESIGN_APPROVED','PAYMENT_RECORDED','REFUND_RECORDED','CONTACT_UPDATED','NOTE','COURIER_UPDATED')),
    from_status varchar(20) CHECK (from_status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED')),
    to_status varchar(20) CHECK (to_status IN ('NEW','DESIGN_SENT','CONFIRMED','MAKING','SHIPPED','DELIVERED','CANCELLED')),
    note text,
    actor varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX order_event_order_idx ON order_event(order_id);
CREATE TABLE order_code_counter (
    year_month char(6) PRIMARY KEY CHECK (year_month ~ '^[0-9]{4}(0[1-9]|1[0-2])$'),
    last_value bigint NOT NULL CHECK (last_value > 0)
);
CREATE TABLE setting (
    key varchar(100) PRIMARY KEY,
    value text NOT NULL
);
