-- One-page order sites: per-product configuration, private landing images, and order snapshots.
ALTER TABLE product ADD COLUMN landing_config jsonb;

-- Landing images use the same storage and visibility rules but stay out of the product gallery.
ALTER TABLE product_image
    ADD COLUMN purpose varchar(10) NOT NULL DEFAULT 'GALLERY' CHECK (purpose IN ('GALLERY','LANDING'));

ALTER TABLE orders
    ADD COLUMN extras_snapshot integer NOT NULL DEFAULT 0
        CHECK (extras_snapshot >= 0 AND extras_snapshot <= 1000000),
    ADD COLUMN landing_selection jsonb,
    ADD COLUMN needed_by date;

-- Orders from a landing page carry a small advance. Earlier orders keep their zero advance.
ALTER TABLE orders DROP CONSTRAINT orders_cod_no_advance;
ALTER TABLE orders ADD CONSTRAINT orders_advance_range CHECK (advance_required BETWEEN 0 AND total);

-- The arithmetic rule now includes package options and extras.
ALTER TABLE orders DROP CONSTRAINT orders_check1;
ALTER TABLE orders ADD CONSTRAINT orders_total_adds_up CHECK (
    total::bigint = unit_price_snapshot::bigint * quantity + extras_snapshot + delivery_charge_snapshot
);

-- Editable placeholders. Both stay private until the owner completes and publishes them.
-- Reviews, customer counts and urgency stay empty: those sections appear only with real content.
INSERT INTO product (slug, title_bn, price, size_bn, indoor_only, sort_order, landing_config) VALUES
('nameplate', 'নেমপ্লেট', 899, NULL, true, 10, $json$
{"enabled":true,
 "hero":{"headline":"আপনার বাড়ির নাম, সুন্দর করে খোদাই করা","subHeadline":"নিচে নিজের নাম লিখে দেখে নিন","heroImage":"","oldPrice":1099,"offerPrice":899},
 "benefits":["লেজারে পরিষ্কার খোদাই","বানানোর আগে ডিজাইন দেখে সম্মতি","সারাদেশে কুরিয়ারে ডেলিভারি","বাংলা ও ইংরেজি দুই ভাষাতেই"],
 "socialProof":{"customerImages":[],"reviewText":"","reviewerName":"","reviewerArea":"","countNumber":null,"proofLine":""},
 "packages":[
  {"name":"সাধারণ","description":"একটি নেমপ্লেট","price":899,"oldPrice":1099,"isPopular":false},
  {"name":"জনপ্রিয়","description":"একটি নেমপ্লেট ও লাগানোর সামগ্রী","price":999,"oldPrice":1199,"isPopular":true},
  {"name":"প্রিমিয়াম","description":"দুটি নেমপ্লেট ও লাগানোর সামগ্রী","price":1699,"oldPrice":2099,"isPopular":false}],
 "urgencyLine":"",
 "options":[{"label":"মাপ","type":"SIZE","values":[{"name":"৫ × ১০ ইঞ্চি","image":"","priceDelta":0},{"name":"৬ × ১২ ইঞ্চি","image":"","priceDelta":200}]}],
 "upsells":[{"label":"উপহারের র‍্যাপিং","price":80}],
 "advanceAmount":100,
 "delivery":{"insideDhakaCharge":60,"outsideDhakaCharge":110},
 "preview":{"designs":[{"name":"ক্লাসিক","baseImage":"","textX":500,"textY":250,"maxWidth":800,"fontSize":110,"minFontSize":40,"color":"#2B211C","shadow":"#FFFFFF","imageWidth":1000,"imageHeight":500}],
  "fonts":["Noto Sans Bengali","Hind Siliguri","Noto Serif Bengali","Galada"],"defaultText":"রহমান ভিলা","maxChars":24}}
$json$::jsonb),
('keyring', 'নামের চাবির রিং', 299, NULL, false, 11, $json$
{"enabled":true,
 "hero":{"headline":"আপনার নাম, আপনার চাবির রিংয়ে","subHeadline":"নিচে নিজের নাম লিখে দেখে নিন","heroImage":"","oldPrice":399,"offerPrice":299},
 "benefits":["লেজারে পরিষ্কার খোদাই","বানানোর আগে ডিজাইন দেখে সম্মতি","সারাদেশে কুরিয়ারে ডেলিভারি","উপহার হিসেবে দারুণ"],
 "socialProof":{"customerImages":[],"reviewText":"","reviewerName":"","reviewerArea":"","countNumber":null,"proofLine":""},
 "packages":[
  {"name":"একটি","description":"একটি চাবির রিং","price":299,"oldPrice":399,"isPopular":false},
  {"name":"জোড়া","description":"দুটি চাবির রিং","price":499,"oldPrice":699,"isPopular":true},
  {"name":"পরিবার","description":"চারটি চাবির রিং","price":899,"oldPrice":1299,"isPopular":false}],
 "urgencyLine":"",
 "options":[{"label":"আকৃতি","type":"SHAPE","values":[{"name":"গোল","image":"","priceDelta":0},{"name":"হার্ট","image":"","priceDelta":30}]}],
 "upsells":[{"label":"উপহারের বক্স","price":60}],
 "advanceAmount":50,
 "delivery":{"insideDhakaCharge":60,"outsideDhakaCharge":110},
 "preview":{"designs":[{"name":"ক্লাসিক","baseImage":"","textX":500,"textY":250,"maxWidth":760,"fontSize":100,"minFontSize":36,"color":"#2B211C","shadow":"#FFFFFF","imageWidth":1000,"imageHeight":500}],
  "fonts":["Noto Sans Bengali","Hind Siliguri","Noto Serif Bengali","Galada"],"defaultText":"রহমান","maxChars":16}}
$json$::jsonb);
