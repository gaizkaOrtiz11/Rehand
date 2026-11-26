-- =====================================================================
-- Rehand demo offers (idempotent: INSERT IGNORE with fixed ids from 7000)
-- =====================================================================
-- Offers between the demo members ausra_k (user 2) and mikel_b (user 3) on listings that are ACTIVE
-- after demo-listings.sql and demo-orders.sql. Runs after both (see config/DemoDataLoader.java) or by hand:
--     mysql -u rehand -p rehand < src/main/resources/demo-offers.sql
--
--   offer  listing                                  seller  buyer  made by  amount  status
--   7000   1000 Quilted puffer jacket (38.00)       ausra   mikel  BUYER     30.00  PENDING (ausra must answer)
--   7001   1006 City bike (185.00)                  mikel   ausra  BUYER    150.00  COUNTERED
--   7002   1006 City bike, counter of 7001          mikel   ausra  SELLER   170.00  PENDING (ausra must answer)
--   7003   1010 13 inch laptop (520.00)             ausra   mikel  BUYER    400.00  REJECTED
--   7004   1008 Over-ear wireless headphones (62.00) mikel  ausra  BUYER     45.00  EXPIRED
--
-- No ACCEPTED offer on purpose: it would need a RESERVED listing and would expire 24 hours later.
-- Times are relative to the first run (NOW() when the row is inserted) and never change afterwards,
-- so the two PENDING offers expire about 40 hours after the first start (the scheduler then marks them).
-- Do not put a semicolon inside a text value: the script runner splits statements at semicolons.
-- =====================================================================

INSERT IGNORE INTO offers (id, listing_id, buyer_id, parent_offer_id, made_by, amount, status, created_at, expires_at) VALUES
    (7000, 1000, 3, NULL, 'BUYER',   30.00, 'PENDING',   DATE_SUB(NOW(), INTERVAL 8 HOUR),  DATE_ADD(NOW(), INTERVAL 40 HOUR)),
    (7001, 1006, 2, NULL, 'BUYER',  150.00, 'COUNTERED', DATE_SUB(NOW(), INTERVAL 30 HOUR), DATE_ADD(NOW(), INTERVAL 18 HOUR)),
    (7003, 1010, 3, NULL, 'BUYER',  400.00, 'REJECTED',  DATE_SUB(NOW(), INTERVAL 6 DAY),   DATE_SUB(NOW(), INTERVAL 4 DAY)),
    (7004, 1008, 2, NULL, 'BUYER',   45.00, 'EXPIRED',   DATE_SUB(NOW(), INTERVAL 9 DAY),   DATE_SUB(NOW(), INTERVAL 7 DAY));

-- The counter-offer points at 7001, so it is inserted after it.
INSERT IGNORE INTO offers (id, listing_id, buyer_id, parent_offer_id, made_by, amount, status, created_at, expires_at) VALUES
    (7002, 1006, 2, 7001, 'SELLER', 170.00, 'PENDING',   DATE_SUB(NOW(), INTERVAL 8 HOUR),  DATE_ADD(NOW(), INTERVAL 40 HOUR));

INSERT IGNORE INTO notifications (id, user_id, type, message, link_url, is_read, created_at) VALUES
    (7000, 2, 'OFFER_RECEIVED', 'mikel_b offered €30.00 for "Quilted puffer jacket, forest green" (asking €38.00). Answer within 48 hours.', '/offers/7000', FALSE, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
    (7001, 3, 'OFFER_RECEIVED', 'ausra_k offered €150.00 for "City bike, 7 speed, green frame" (asking €185.00). Answer within 48 hours.', '/offers/7001', TRUE, DATE_SUB(NOW(), INTERVAL 30 HOUR)),
    (7002, 2, 'OFFER_RECEIVED', 'mikel_b countered with €170.00 for "City bike, 7 speed, green frame". Answer within 48 hours.', '/offers/7002', FALSE, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
    (7003, 3, 'OFFER_ANSWERED', 'The seller declined your offer of €400.00 for "13 inch laptop, 16 GB RAM, 512 GB SSD".', '/offers/7003', TRUE, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (7004, 2, 'OFFER_ANSWERED', 'Your offer of €45.00 on "Over-ear wireless headphones" expired without an answer.', '/offers/7004', TRUE, DATE_SUB(NOW(), INTERVAL 7 DAY));


-- Older databases: demo listings 1009 and 1011 were seeded as RESERVED without an accepted offer.
-- Put them back on sale unless a real accepted offer now holds them. Safe to run on every start.
UPDATE listings l SET l.status = 'ACTIVE'
WHERE l.id IN (1009, 1011) AND l.status = 'RESERVED'
  AND NOT EXISTS (SELECT 1 FROM offers o WHERE o.listing_id = l.id AND o.status = 'ACCEPTED');
