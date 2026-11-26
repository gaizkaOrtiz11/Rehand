package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReturnToTest {

    @Test
    void acceptsLocalPaths() {
        assertThat(ReturnTo.sanitize("/orders/checkout/1001")).isEqualTo("/orders/checkout/1001");
        assertThat(ReturnTo.sanitize("/listings/1001/buy?offer=4")).isEqualTo("/listings/1001/buy?offer=4");
        assertThat(ReturnTo.sanitize("  /wallet  ")).isEqualTo("/wallet");
    }

    @Test
    void rejectsExternalAndOddTargets() {
        assertThat(ReturnTo.sanitize("//evil.example/x")).isNull();
        assertThat(ReturnTo.sanitize("/\\evil.example")).isNull();
        assertThat(ReturnTo.sanitize("https://evil.example")).isNull();
        assertThat(ReturnTo.sanitize("javascript:alert(1)")).isNull();
        assertThat(ReturnTo.sanitize("orders/1")).isNull();
        assertThat(ReturnTo.sanitize("/a b")).isNull();
        assertThat(ReturnTo.sanitize("/a\nLocation: x")).isNull();
        assertThat(ReturnTo.sanitize("/x/{id}")).isNull();
        assertThat(ReturnTo.sanitize("")).isNull();
        assertThat(ReturnTo.sanitize(null)).isNull();
        assertThat(ReturnTo.sanitize("/" + "a".repeat(ReturnTo.MAX_LENGTH))).isNull();
    }
}
