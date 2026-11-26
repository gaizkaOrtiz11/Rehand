-- =====================================================================
-- Rehand demo orders (idempotent: INSERT IGNORE with fixed ids from 5000)
-- =====================================================================
-- Five orders between the demo members ausra_k (user and wallet 2) and mikel_b (user and wallet 3),
-- one in every status, with the matching wallet ledger rows. Runs after demo-listings.sql
-- (see config/DemoDataLoader.java) or by hand:
--     mysql -u rehand -p rehand < src/main/resources/demo-orders.sql
--
--   order  listing                                   seller  buyer  price   status
--   5000   1014 Data structures textbook (SOLD)      mikel   ausra   22.00  COMPLETED
--   5001   1018 Compact travel camera (SOLD)         mikel   ausra  120.00  SHIPPED
--   5002   1015 Linen shade table lamp               ausra   mikel   18.00  DELIVERED (20 h ago)
--   5003   1023 Comic book bundle                    ausra   mikel   12.00  PAID
--   5004   1013 Calculus textbooks (stays ACTIVE)    ausra   mikel   30.00  CANCELLED
--
-- Ledger, oldest first (balance_after is the wallet balance right after the row):
--   ausra  #1 TOP_UP 150.00 -> 150.00   #5000 PAYMENT 22.00 -> 128.00   #5005 PAYMENT 120.00 -> 8.00
--   mikel  #2 TOP_UP  75.50 ->  75.50   #5001 SALE_CREDIT 22.00 -> 97.50   #5002 PAYMENT 30.00 -> 67.50
--          #5003 REFUND 30.00 -> 97.50  #5004 PAYMENT 18.00 -> 79.50       #5006 PAYMENT 12.00 -> 67.50
-- Final balances: ausra 8.00, mikel 67.50.
--
-- Times are relative to the first run (NOW() when the row is inserted) and never change afterwards.
-- Do not put a semicolon inside a text value: the script runner splits statements at semicolons.
-- =====================================================================

INSERT IGNORE INTO orders
    (id, listing_id, buyer_id, offer_id, price, ship_name, ship_street, ship_city, ship_postal_code,
     tracking_code, status, created_at, shipped_at, delivered_at, completed_at, cancelled_at) VALUES
    (5000, 1014, 2, NULL,  22.00, 'Aušra Kazlauskienė', 'Laisvės al. 53-12', 'Kaunas', 'LT-44309',
        'LP004417852LT', 'COMPLETED', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY),
        DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),
    (5001, 1018, 2, NULL, 120.00, 'Aušra Kazlauskienė', 'Laisvės al. 53-12', 'Kaunas', 'LT-44309',
        'OMNIVA-CC93017264', 'SHIPPED', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY),
        NULL, NULL, NULL),
    (5002, 1015, 3, NULL,  18.00, 'Mikel Bengoetxea', 'Gedimino pr. 9-4', 'Vilnius', 'LT-01103',
        'LP004530918LT', 'DELIVERED', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY),
        DATE_SUB(NOW(), INTERVAL 20 HOUR), NULL, NULL),
    (5003, 1023, 3, NULL,  12.00, 'Mikel Bengoetxea', 'Gedimino pr. 9-4', 'Vilnius', 'LT-01103',
        NULL, 'PAID', DATE_SUB(NOW(), INTERVAL 6 HOUR), NULL, NULL, NULL, NULL),
    (5004, 1013, 3, NULL,  30.00, 'Mikel Bengoetxea', 'Gedimino pr. 9-4', 'Vilnius', 'LT-01103',
        NULL, 'CANCELLED', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY));

-- The two listings bought from ausra are sold while their order is not cancelled.
-- (The join makes this a no-op once a demo order was cancelled in the app.)
UPDATE listings l
    JOIN orders o ON o.listing_id = l.id
    SET l.status = 'SOLD'
    WHERE o.id IN (5002, 5003) AND o.status <> 'CANCELLED' AND l.status = 'ACTIVE';

-- The opening top-ups of data.sql get a date before the first demo order, so the ledger reads in order.
UPDATE wallet_transactions t
    JOIN orders o ON o.id = 5000
    SET t.created_at = DATE_SUB(o.created_at, INTERVAL 2 DAY)
    WHERE t.id IN (1, 2) AND t.type = 'TOP_UP' AND t.created_at > DATE_SUB(o.created_at, INTERVAL 2 DAY);

INSERT IGNORE INTO wallet_transactions (id, wallet_id, order_id, type, amount, balance_after, created_at) VALUES
    (5000, 2, 5000, 'PAYMENT',      22.00, 128.00, DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (5001, 3, 5000, 'SALE_CREDIT',  22.00,  97.50, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (5002, 3, 5004, 'PAYMENT',      30.00,  67.50, DATE_SUB(NOW(), INTERVAL 8 DAY)),
    (5003, 3, 5004, 'REFUND',       30.00,  97.50, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (5004, 3, 5002, 'PAYMENT',      18.00,  79.50, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (5005, 2, 5001, 'PAYMENT',     120.00,   8.00, DATE_SUB(NOW(), INTERVAL 4 DAY)),
    (5006, 3, 5003, 'PAYMENT',      12.00,  67.50, DATE_SUB(NOW(), INTERVAL 6 HOUR));

-- The cached balance follows the ledger: money in (TOP_UP, REFUND, SALE_CREDIT) minus money out.
-- Recomputing it on every start is harmless because every balance change writes a ledger row.
UPDATE wallets w
    JOIN (SELECT wallet_id,
                 SUM(CASE WHEN type IN ('TOP_UP', 'REFUND', 'SALE_CREDIT') THEN amount ELSE -amount END) AS total
          FROM wallet_transactions
          WHERE wallet_id IN (2, 3)
          GROUP BY wallet_id) s ON s.wallet_id = w.id
    SET w.balance = s.total
    WHERE w.id IN (2, 3) AND w.balance <> s.total AND s.total >= 0;

INSERT IGNORE INTO notifications (id, user_id, type, message, link_url, is_read, created_at) VALUES
    (5000, 3, 'ORDER_STATUS', '€22.00 for "Data structures and algorithms textbook" is now in your wallet.', '/orders/5000', TRUE, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (5001, 2, 'ORDER_STATUS', '"Compact travel camera" is on its way. Tracking code: OMNIVA-CC93017264.', '/orders/5001', FALSE, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (5002, 2, 'ORDER_STATUS', 'mikel_b received "Linen shade table lamp". The payment is released after their check, at the latest in 48 hours.', '/orders/5002', FALSE, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
    (5003, 2, 'ITEM_SOLD', 'mikel_b bought "Comic book bundle, 12 issues" for €12.00. Ship it and add the tracking code.', '/orders/5003', FALSE, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
    (5004, 2, 'ORDER_STATUS', 'mikel_b cancelled the order for "Calculus and linear algebra textbooks". Your listing is for sale again. Reason: Found a copy in the library.', '/orders/5004', TRUE, DATE_SUB(NOW(), INTERVAL 7 DAY));
