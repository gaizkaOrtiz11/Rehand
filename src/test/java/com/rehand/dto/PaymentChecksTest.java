package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class PaymentChecksTest {

    private static final YearMonth NOW = YearMonth.of(2026, 11);

    @Test
    void luhnAcceptsKnownTestCards() {
        assertThat(PaymentChecks.isValidCardNumber("4242424242424242")).isTrue();
        assertThat(PaymentChecks.isValidCardNumber("4242 4242 4242 4242")).isTrue();
        assertThat(PaymentChecks.isValidCardNumber("5555 5555 5555 4444")).isTrue();
        assertThat(PaymentChecks.isValidCardNumber("378282246310005")).isTrue();
    }

    @Test
    void luhnRejectsBadNumbers() {
        assertThat(PaymentChecks.isValidCardNumber("4242424242424241")).isFalse();
        assertThat(PaymentChecks.isValidCardNumber("424242424242")).isFalse();
        assertThat(PaymentChecks.isValidCardNumber("42424242424242424242")).isFalse();
        assertThat(PaymentChecks.isValidCardNumber("4242-4242-4242-4242")).isFalse();
        assertThat(PaymentChecks.isValidCardNumber("abcd")).isFalse();
        assertThat(PaymentChecks.isValidCardNumber(null)).isFalse();
    }

    @Test
    void expiryMustNotBeInThePast() {
        assertThat(PaymentChecks.isValidExpiry("11/26", NOW)).isTrue();
        assertThat(PaymentChecks.isValidExpiry("12 / 26", NOW)).isTrue();
        assertThat(PaymentChecks.isValidExpiry("01/30", NOW)).isTrue();
        assertThat(PaymentChecks.isValidExpiry("10/26", NOW)).isFalse();
        assertThat(PaymentChecks.isValidExpiry("13/27", NOW)).isFalse();
        assertThat(PaymentChecks.isValidExpiry("00/27", NOW)).isFalse();
        assertThat(PaymentChecks.isValidExpiry("1227", NOW)).isFalse();
        assertThat(PaymentChecks.isValidExpiry("12/2027", NOW)).isFalse();
        assertThat(PaymentChecks.isValidExpiry(null, NOW)).isFalse();
    }

    @Test
    void ibanAcceptsValidNumbers() {
        assertThat(PaymentChecks.isValidIban("LT12 1000 0111 0100 1000")).isTrue();
        assertThat(PaymentChecks.isValidIban("ES91 2100 0418 4502 0005 1332")).isTrue();
        assertThat(PaymentChecks.isValidIban("gb82 west 1234 5698 7654 32")).isTrue();
        assertThat(PaymentChecks.isValidIban("DE89370400440532013000")).isTrue();
    }

    @Test
    void ibanRejectsWrongChecksumOrFormat() {
        assertThat(PaymentChecks.isValidIban("LT13 1000 0111 0100 1000")).isFalse();
        assertThat(PaymentChecks.isValidIban("1T12 1000 0111 0100 1000")).isFalse();
        assertThat(PaymentChecks.isValidIban("LTAB 1000 0111 0100 1000")).isFalse();
        assertThat(PaymentChecks.isValidIban("LT12-1000-0111-0100-1000")).isFalse();
        assertThat(PaymentChecks.isValidIban("LT12")).isFalse();
        assertThat(PaymentChecks.isValidIban(null)).isFalse();
    }

    @Test
    void ibanTailShowsOnlyLastFour() {
        assertThat(PaymentChecks.ibanTail("LT12 1000 0111 0100 1000")).isEqualTo("1000");
        assertThat(PaymentChecks.ibanTail(null)).isEmpty();
    }
}
