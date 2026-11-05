-- Trial values only. All products stay DRAFT and ordering stays disabled.
INSERT INTO product (slug, title_bn, price, size_bn, indoor_only, sort_order) VALUES
('engraved-keyring', 'নাম খোদাই করা চাবির রিং', NULL, NULL, false, 1),
('keyring-gift-pair', 'দুটি চাবির রিংয়ের উপহার-জোড়া', 399, NULL, false, 2),
('door-name-plate', 'দরজার নেমপ্লেট', 899, '৫ × ১০ ইঞ্চি', true, 3),
('desk-name-plate', 'অফিস ডেস্কের নেমপ্লেট', NULL, NULL, true, 4);
INSERT INTO setting (key, value) VALUES
('phone', ''), ('whatsapp', ''), ('bkash_number', ''), ('business_email', ''),
('delivery_inside', '60'), ('delivery_outside', '110'),
('courier_days_inside_min', ''), ('courier_days_inside_max', ''),
('courier_days_outside_min', ''), ('courier_days_outside_max', ''),
('reply_hours', ''), ('accepting_orders', 'false'),
('pause_notice', 'এখনো অনলাইনে অর্ডার নেওয়া শুরু হয়নি।');
