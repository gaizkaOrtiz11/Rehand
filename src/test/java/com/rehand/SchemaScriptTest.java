package com.rehand;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.rehand.domain.ItemCondition;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.NotificationType;
import com.rehand.domain.OfferStatus;
import com.rehand.domain.OrderStatus;
import com.rehand.domain.ReportReason;
import com.rehand.domain.ReportStatus;
import com.rehand.domain.Role;
import com.rehand.domain.TransactionType;
import com.rehand.domain.UserStatus;

/**
 * Plain JUnit test (no Spring context, no database): checks that schema.sql defines
 * the 16 tables of the ER diagram and that its CHECK lists contain every enum constant.
 */
class SchemaScriptTest {

    private static final List<String> TABLES = List.of(
            "users", "wallets", "wallet_transactions", "categories", "listings", "listing_photos",
            "offers", "orders", "conversations", "messages", "reviews", "favorites", "follows",
            "saved_searches", "notifications", "reports");

    private static String read(String name) throws IOException {
        try (InputStream in = SchemaScriptTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name + " is on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void schemaCreatesAllSixteenTables() throws IOException {
        String sql = read("schema.sql");
        Matcher m = Pattern.compile("^CREATE TABLE IF NOT EXISTS (\\w+)", Pattern.MULTILINE).matcher(sql);
        List<String> found = m.results().map(r -> r.group(1)).toList();

        assertThat(found).hasSize(16);
        assertThat(found).containsExactlyInAnyOrderElementsOf(TABLES);
    }

    @Test
    void schemaCheckConstraintsListEveryEnumConstant() throws IOException {
        String sql = read("schema.sql");
        for (Enum<?>[] constants : new Enum<?>[][] {
                Role.values(), UserStatus.values(), ListingStatus.values(), ItemCondition.values(),
                OfferStatus.values(), OrderStatus.values(), TransactionType.values(),
                NotificationType.values(), ReportReason.values(), ReportStatus.values() }) {
            for (Enum<?> c : constants) {
                assertThat(sql).as("schema.sql mentions " + c.name()).contains("'" + c.name() + "'");
            }
        }
    }

    @Test
    void seedScriptExists() throws IOException {
        String sql = read("data.sql");
        assertThat(sql).contains("INSERT IGNORE INTO categories").contains("INSERT IGNORE INTO users");
    }
}
