-- =====================================================================
-- Rehand database schema (MySQL 8.0.16+ or MariaDB 10.5+), 16 tables
-- =====================================================================
-- Source of truth for the database. Hibernate only validates it
-- (spring.jpa.hibernate.ddl-auto=validate).
--
-- Idempotent: every table is created only if missing, so the script is safe
-- to run on every start (spring.sql.init.mode=always) and by hand.
--
-- Run it by hand (after creating the database and user, see README.md):
--     mysql -u rehand -p rehand < src/main/resources/schema.sql
--     mysql -u rehand -p rehand < src/main/resources/data.sql
-- or from the mysql prompt:
--     SOURCE src/main/resources/schema.sql
--
-- Conventions
--   * Money is DECIMAL(10,2). Dates are DATETIME. Text is utf8mb4.
--   * Enumerations are VARCHAR + CHECK (values match the Java enums in
--     com.rehand.domain exactly).
--   * MySQL forbids CHECK constraints on columns that also have an
--     ON DELETE CASCADE or SET NULL foreign key. Tables follows and
--     reports have such CHECKs, so their foreign keys use the default
--     RESTRICT: delete the follow rows (or hide the listing) first.
--   * Statements end with a semicolon at the end of a line, and no
--     comment or string contains one.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    username      VARCHAR(40)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    city          VARCHAR(80)  NULL,
    bio           VARCHAR(500) NULL,
    avatar_path   VARCHAR(255) NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_role    CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT chk_users_status  CHECK (status IN ('ACTIVE', 'BANNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. wallets (one per user)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    balance    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user    UNIQUE (user_id),
    CONSTRAINT fk_wallets_user    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. categories (self-referencing tree, up to 3 levels)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT      NULL,
    name       VARCHAR(80) NOT NULL,
    slug       VARCHAR(90) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug   UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    INDEX idx_categories_parent_sort (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. listings
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS listings (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT        NOT NULL,
    category_id    BIGINT        NOT NULL,
    title          VARCHAR(120)  NOT NULL,
    description    TEXT          NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    item_condition VARCHAR(20)   NOT NULL,
    brand          VARCHAR(60)   NULL,
    size           VARCHAR(20)   NULL,
    color          VARCHAR(30)   NULL,
    city           VARCHAR(80)   NOT NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    view_count     INT           NOT NULL DEFAULT 0,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_listings_seller   FOREIGN KEY (seller_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_listings_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_listings_price     CHECK (price > 0),
    CONSTRAINT chk_listings_condition CHECK (item_condition IN ('NEW_WITH_TAGS', 'NEW', 'VERY_GOOD', 'GOOD', 'FAIR')),
    CONSTRAINT chk_listings_status    CHECK (status IN ('ACTIVE', 'RESERVED', 'SOLD', 'HIDDEN', 'REMOVED')),
    CONSTRAINT chk_listings_views     CHECK (view_count >= 0),
    INDEX idx_listings_browse    (status, category_id, created_at),
    INDEX idx_listings_status_price (status, price),
    INDEX idx_listings_status_created (status, created_at),
    INDEX idx_listings_seller    (seller_id, status),
    INDEX idx_listings_city      (city),
    FULLTEXT INDEX ftx_listings_title_description (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. listing_photos (1 to 6 per listing, position 0 is the cover)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS listing_photos (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    listing_id BIGINT       NOT NULL,
    file_path  VARCHAR(255) NOT NULL,
    position   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_photos_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT chk_photos_position CHECK (position >= 0),
    INDEX idx_photos_listing_position (listing_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. offers (self-referencing chain of offers and counter-offers)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offers (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    listing_id      BIGINT        NOT NULL,
    buyer_id        BIGINT        NOT NULL,
    parent_offer_id BIGINT        NULL,
    made_by         VARCHAR(10)   NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offers_listing FOREIGN KEY (listing_id)      REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT fk_offers_buyer   FOREIGN KEY (buyer_id)        REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_offers_parent  FOREIGN KEY (parent_offer_id) REFERENCES offers (id)   ON DELETE SET NULL,
    CONSTRAINT chk_offers_made_by CHECK (made_by IN ('BUYER', 'SELLER')),
    CONSTRAINT chk_offers_amount  CHECK (amount > 0),
    CONSTRAINT chk_offers_status  CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'EXPIRED', 'WITHDRAWN')),
    INDEX idx_offers_listing_buyer (listing_id, buyer_id, status),
    INDEX idx_offers_status_expires (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. orders (shipping address is copied onto the order)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    listing_id       BIGINT        NOT NULL,
    buyer_id         BIGINT        NOT NULL,
    offer_id         BIGINT        NULL,
    price            DECIMAL(10,2) NOT NULL,
    ship_name        VARCHAR(100)  NOT NULL,
    ship_street      VARCHAR(200)  NOT NULL,
    ship_city        VARCHAR(80)   NOT NULL,
    ship_postal_code VARCHAR(12)   NOT NULL,
    tracking_code    VARCHAR(60)   NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'PAID',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at       DATETIME      NULL,
    delivered_at     DATETIME      NULL,
    completed_at     DATETIME      NULL,
    cancelled_at     DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_offer    UNIQUE (offer_id),
    CONSTRAINT fk_orders_listing  FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_orders_buyer    FOREIGN KEY (buyer_id)   REFERENCES users (id),
    CONSTRAINT fk_orders_offer    FOREIGN KEY (offer_id)   REFERENCES offers (id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_price   CHECK (price > 0),
    CONSTRAINT chk_orders_status  CHECK (status IN ('PAID', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED')),
    INDEX idx_orders_buyer_status (buyer_id, status),
    INDEX idx_orders_listing      (listing_id),
    INDEX idx_orders_status_delivered (status, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. wallet_transactions (ledger of every balance change)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    wallet_id     BIGINT        NOT NULL,
    order_id      BIGINT        NULL,
    type          VARCHAR(12)   NOT NULL,
    amount        DECIMAL(10,2) NOT NULL,
    balance_after DECIMAL(10,2) NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_wtx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_wtx_order  FOREIGN KEY (order_id)  REFERENCES orders (id)  ON DELETE SET NULL,
    CONSTRAINT chk_wtx_type    CHECK (type IN ('TOP_UP', 'PAYMENT', 'REFUND', 'SALE_CREDIT', 'WITHDRAWAL')),
    CONSTRAINT chk_wtx_amount  CHECK (amount > 0),
    CONSTRAINT chk_wtx_balance CHECK (balance_after >= 0),
    INDEX idx_wtx_wallet_created (wallet_id, created_at),
    INDEX idx_wtx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 9. conversations (one per listing and buyer)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    listing_id BIGINT   NOT NULL,
    buyer_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversations_listing_buyer UNIQUE (listing_id, buyer_id),
    CONSTRAINT fk_conversations_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_buyer   FOREIGN KEY (buyer_id)   REFERENCES users (id)    ON DELETE CASCADE,
    INDEX idx_conversations_buyer (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 10. messages (read_at NULL means unread)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    sent_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender       FOREIGN KEY (sender_id)       REFERENCES users (id)         ON DELETE CASCADE,
    INDEX idx_messages_conversation_sent (conversation_id, sent_at),
    INDEX idx_messages_unread (conversation_id, sender_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 11. reviews (buyer and seller each review the other once per order)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    reviewer_id BIGINT        NOT NULL,
    reviewee_id BIGINT        NOT NULL,
    rating      TINYINT       NOT NULL,
    comment     VARCHAR(1000) NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_order_reviewer UNIQUE (order_id, reviewer_id),
    CONSTRAINT fk_reviews_order    FOREIGN KEY (order_id)    REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT chk_reviews_rating  CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_reviewee (reviewee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 12. favorites (composite primary key)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS favorites (
    user_id    BIGINT   NOT NULL,
    listing_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, listing_id),
    CONSTRAINT fk_favorites_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    INDEX idx_favorites_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 13. follows (composite primary key, no self-follow)
--     Default RESTRICT foreign keys on purpose: see the header note.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS follows (
    follower_id BIGINT   NOT NULL,
    followed_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followed_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id),
    CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users (id),
    CONSTRAINT chk_follows_not_self CHECK (follower_id <> followed_id),
    INDEX idx_follows_followed (followed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 14. saved_searches
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saved_searches (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    name           VARCHAR(80)   NOT NULL,
    keyword        VARCHAR(120)  NULL,
    category_id    BIGINT        NULL,
    min_price      DECIMAL(10,2) NULL,
    max_price      DECIMAL(10,2) NULL,
    item_condition VARCHAR(20)   NULL,
    city           VARCHAR(80)   NULL,
    notify         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_saved_user     FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_saved_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT chk_saved_condition CHECK (item_condition IS NULL OR item_condition IN ('NEW_WITH_TAGS', 'NEW', 'VERY_GOOD', 'GOOD', 'FAIR')),
    CONSTRAINT chk_saved_prices    CHECK (min_price IS NULL OR max_price IS NULL OR min_price <= max_price),
    INDEX idx_saved_user (user_id),
    INDEX idx_saved_notify (notify)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 15. notifications (in-app bell)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    message    VARCHAR(255) NOT NULL,
    link_url   VARCHAR(255) NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type CHECK (type IN ('NEW_MESSAGE', 'OFFER_RECEIVED', 'OFFER_ANSWERED', 'ITEM_SOLD', 'ORDER_STATUS', 'NEW_REVIEW', 'SELLER_POSTED', 'SEARCH_MATCH', 'LISTING_REMOVED')),
    INDEX idx_notifications_user_read (user_id, is_read),
    INDEX idx_notifications_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 16. reports (complaints about a listing or a user)
--     Default RESTRICT foreign keys on purpose: see the header note.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id      BIGINT        NOT NULL,
    listing_id       BIGINT        NULL,
    reported_user_id BIGINT        NULL,
    reason           VARCHAR(20)   NOT NULL,
    details          VARCHAR(1000) NULL,
    status           VARCHAR(15)   NOT NULL DEFAULT 'OPEN',
    handled_by       BIGINT        NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at      DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id)      REFERENCES users (id),
    CONSTRAINT fk_reports_listing  FOREIGN KEY (listing_id)       REFERENCES listings (id),
    CONSTRAINT fk_reports_user     FOREIGN KEY (reported_user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_handler  FOREIGN KEY (handled_by)       REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_reports_reason  CHECK (reason IN ('SPAM', 'PROHIBITED_ITEM', 'FRAUD', 'OFFENSIVE', 'OTHER')),
    CONSTRAINT chk_reports_status  CHECK (status IN ('OPEN', 'DISMISSED', 'ACTION_TAKEN')),
    CONSTRAINT chk_reports_target  CHECK (listing_id IS NOT NULL OR reported_user_id IS NOT NULL),
    INDEX idx_reports_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
