-- =====================================================================
-- Rehand seed data (idempotent: INSERT IGNORE, safe to run on every start)
-- =====================================================================
-- Run by hand after schema.sql:
--     mysql -u rehand -p rehand < src/main/resources/data.sql
--
-- INSERT IGNORE (not ON DUPLICATE KEY UPDATE) on purpose: rows that already
-- exist are left untouched, so a changed password or a renamed category is not
-- reset on the next start.
--
-- Demo accounts (plain passwords are listed in README.md only):
--   admin@rehand.test   role ADMIN
--   ausra@rehand.test   role MEMBER
--   mikel@rehand.test   role MEMBER
-- =====================================================================

-- ---------------------------------------------------------------------
-- Categories: 8 top-level, 3 to 5 children each (ids: parent n, children n1..n5)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO categories (id, parent_id, name, slug, sort_order) VALUES
    (1, NULL, 'Clothing and shoes',       'clothing-shoes',      1),
    (2, NULL, 'Electronics',              'electronics',         2),
    (3, NULL, 'Home and garden',          'home-garden',         3),
    (4, NULL, 'Books, music and film',    'books-music-film',    4),
    (5, NULL, 'Sports and outdoors',      'sports-outdoors',     5),
    (6, NULL, 'Kids and baby',            'kids-baby',           6),
    (7, NULL, 'Hobbies and collectibles', 'hobbies-collectibles', 7),
    (8, NULL, 'Beauty and health',        'beauty-health',       8);

INSERT IGNORE INTO categories (id, parent_id, name, slug, sort_order) VALUES
    (11, 1, 'Women''s clothing',     'womens-clothing',      1),
    (12, 1, 'Men''s clothing',       'mens-clothing',        2),
    (13, 1, 'Shoes',                 'shoes',                3),
    (14, 1, 'Bags and accessories',  'bags-accessories',     4),

    (21, 2, 'Phones',                'phones',               1),
    (22, 2, 'Laptops and tablets',   'laptops-tablets',      2),
    (23, 2, 'Audio and headphones',  'audio-headphones',     3),
    (24, 2, 'Cameras',               'cameras',              4),
    (25, 2, 'Gaming',                'gaming',               5),

    (31, 3, 'Furniture',             'furniture',            1),
    (32, 3, 'Kitchen and dining',    'kitchen-dining',       2),
    (33, 3, 'Decor and lighting',    'decor-lighting',       3),
    (34, 3, 'Garden and tools',      'garden-tools',         4),

    (41, 4, 'Books',                 'books',                1),
    (42, 4, 'Textbooks and study',   'textbooks-study',      2),
    (43, 4, 'Vinyl and CDs',         'vinyl-cds',            3),
    (44, 4, 'Comics and magazines',  'comics-magazines',     4),

    (51, 5, 'Bikes',                 'bikes',                1),
    (52, 5, 'Fitness',               'fitness',              2),
    (53, 5, 'Camping and hiking',    'camping-hiking',       3),
    (54, 5, 'Winter sports',         'winter-sports',        4),
    (55, 5, 'Fishing',               'fishing',              5),

    (61, 6, 'Toys',                  'toys',                 1),
    (62, 6, 'Baby gear',             'baby-gear',            2),
    (63, 6, 'Kids'' clothing',       'kids-clothing',        3),

    (71, 7, 'Musical instruments',   'musical-instruments',  1),
    (72, 7, 'Board games and puzzles', 'board-games-puzzles', 2),
    (73, 7, 'Art and craft supplies', 'art-craft-supplies',  3),
    (74, 7, 'Collectibles',          'collectibles',         4),

    (81, 8, 'Skincare',              'skincare',             1),
    (82, 8, 'Makeup',                'makeup',               2),
    (83, 8, 'Fragrance',             'fragrance',            3);

-- ---------------------------------------------------------------------
-- Users: one admin, two demo members (BCrypt cost 10)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO users (id, email, username, password_hash, city, bio, role, status) VALUES
    (1, 'admin@rehand.test', 'admin',
        '$2b$10$7EyLv01dWTwZeaMMDfpVhuuq9oOMiUfacE0VuseKigd8PEssW/CNi',
        'Kaunas', 'Rehand administrator.', 'ADMIN', 'ACTIVE'),
    (2, 'ausra@rehand.test', 'ausra_k',
        '$2b$10$IJiwSgAD/4XtgNLNxeb.M.SPdya8B3sjcwR5QoQ5ILQIpMUfM7j16',
        'Kaunas', 'Selling things I no longer use. Quick replies.', 'MEMBER', 'ACTIVE'),
    (3, 'mikel@rehand.test', 'mikel_b',
        '$2b$10$IJiwSgAD/4XtgNLNxeb.M.SPdya8B3sjcwR5QoQ5ILQIpMUfM7j16',
        'Vilnius', 'Student, mostly books and bike parts.', 'MEMBER', 'ACTIVE');

-- ---------------------------------------------------------------------
-- Wallets: one per user (wallet id equals user id)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO wallets (id, user_id, balance) VALUES
    (1, 1, 0.00),
    (2, 2, 150.00),
    (3, 3, 75.50);

-- Opening top-ups so every non-zero balance has a ledger entry
INSERT IGNORE INTO wallet_transactions (id, wallet_id, order_id, type, amount, balance_after) VALUES
    (1, 2, NULL, 'TOP_UP', 150.00, 150.00),
    (2, 3, NULL, 'TOP_UP',  75.50,  75.50);
