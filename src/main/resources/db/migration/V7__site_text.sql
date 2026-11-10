-- Customer-facing wording the owner has changed in Admin → Site text.
-- Only edited texts are stored; everything else uses the defaults in site-text.properties.
-- An empty value hides an optional text.
CREATE TABLE site_text (
    key varchar(120) PRIMARY KEY CHECK (key ~ '^[a-z0-9]+([._-][a-z0-9]+)*$'),
    value text NOT NULL CHECK (char_length(value) <= 2000),
    updated_at timestamptz NOT NULL DEFAULT now()
);
