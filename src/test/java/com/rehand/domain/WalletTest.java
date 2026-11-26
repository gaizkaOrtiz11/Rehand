package com.rehand.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.rehand.exception.BusinessRuleException;

class WalletTest {

    private static Wallet wallet(String balance) {
        return new Wallet(new User(), new BigDecimal(balance));
    }

    @Test
    void hasFundsComparesWithTheBalance() {
        Wallet w = wallet("50.00");
        assertThat(w.hasFunds(new BigDecimal("50.00"))).isTrue();
        assertThat(w.hasFunds(new BigDecimal("49.99"))).isTrue();
        assertThat(w.hasFunds(new BigDecimal("50.01"))).isFalse();
        assertThat(w.hasFunds(null)).isTrue();
    }

    @Test
    void debitLowersBalanceAndReturnsTransaction() {
        Wallet w = wallet("100.00");
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(7L);

        WalletTransaction tx = w.debit(new BigDecimal("40.5"), TransactionType.PAYMENT, order);

        assertThat(w.getBalance()).isEqualByComparingTo("59.50");
        assertThat(tx.getWallet()).isSameAs(w);
        assertThat(tx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(tx.getAmount()).isEqualTo(new BigDecimal("40.50"));
        assertThat(tx.getBalanceAfter()).isEqualTo(new BigDecimal("59.50"));
        assertThat(tx.getOrder()).isSameAs(order);
        assertThat(tx.getOrderId()).isEqualTo(7L);
        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(tx.isCredit()).isFalse();
    }

    @Test
    void debitOfTheWholeBalanceLeavesZero() {
        Wallet w = wallet("25.00");
        w.debit(new BigDecimal("25.00"), TransactionType.WITHDRAWAL, null);
        assertThat(w.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void debitRefusesWhenBalanceTooLow() {
        Wallet w = wallet("10.00");
        assertThatThrownBy(() -> w.debit(new BigDecimal("10.01"), TransactionType.PAYMENT, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Your wallet balance is too low.");
        assertThat(w.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void creditRaisesBalance() {
        Wallet w = wallet("0.00");
        WalletTransaction tx = w.credit(new BigDecimal("25"), TransactionType.TOP_UP, null);
        assertThat(w.getBalance()).isEqualByComparingTo("25.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("25.00");
        assertThat(tx.getOrderId()).isNull();
        assertThat(tx.isCredit()).isTrue();
    }

    @Test
    void amountsMustBePositive() {
        Wallet w = wallet("10.00");
        assertThatThrownBy(() -> w.credit(BigDecimal.ZERO, TransactionType.TOP_UP, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> w.debit(new BigDecimal("-1"), TransactionType.PAYMENT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> w.credit(null, TransactionType.REFUND, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(w.getBalance()).isEqualByComparingTo("10.00");
    }
}
