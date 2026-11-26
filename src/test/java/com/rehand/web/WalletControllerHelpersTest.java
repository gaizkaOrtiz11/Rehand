package com.rehand.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WalletControllerHelpersTest {

    @Test
    void eurosUsesTwoDecimalsAndGrouping() {
        assertThat(WalletController.euros(new BigDecimal("1234.5"))).isEqualTo("€1,234.50");
        assertThat(WalletController.euros(new BigDecimal("0"))).isEqualTo("€0.00");
    }

    @Test
    void parseAmountIgnoresOddValues() {
        assertThat(WalletController.parseAmount("12.30")).isEqualByComparingTo("12.30");
        assertThat(WalletController.parseAmount("12,30")).isEqualByComparingTo("12.30");
        assertThat(WalletController.parseAmount("abc")).isNull();
        assertThat(WalletController.parseAmount("-5")).isNull();
        assertThat(WalletController.parseAmount("")).isNull();
        assertThat(WalletController.parseAmount(null)).isNull();
    }
}
