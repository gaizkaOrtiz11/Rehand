/**
 * Domain model: JPA entities (added from step 2 on) and the enumerations.
 *
 * <h2>Notes for entity authors (schema.sql is the source of truth, ddl-auto=validate)</h2>
 * <ul>
 *   <li>Enum columns are {@code VARCHAR} with a CHECK constraint, not MySQL ENUM. Map them with
 *       {@code @Enumerated(EnumType.STRING)} and, to be safe with Hibernate's schema validation on MySQL,
 *       add {@code @JdbcTypeCode(SqlTypes.VARCHAR)} (org.hibernate.annotations.JdbcTypeCode,
 *       org.hibernate.type.SqlTypes).</li>
 *   <li>{@code listing_photos.position}, {@code reviews.rating} are {@code TINYINT}. Map an {@code int} field with
 *       {@code @JdbcTypeCode(SqlTypes.TINYINT)} (or use {@code byte}), otherwise validation reports
 *       "found tinyint, expecting integer".</li>
 *   <li>{@code listings.item_condition} is the column of the {@code condition} field (CONDITION is a reserved
 *       word in MySQL): {@code @Column(name = "item_condition")}.</li>
 *   <li>{@code listings.description} is {@code TEXT}: {@code @Column(columnDefinition = "TEXT")}.</li>
 *   <li>Boolean columns ({@code notify}, {@code is_read}) are {@code BOOLEAN} (TINYINT(1)).
 *       Notification.read maps to {@code @Column(name = "is_read")}.</li>
 *   <li>Money is {@code DECIMAL(10,2)}: use {@code BigDecimal}.</li>
 * </ul>
 */
package com.rehand.domain;
