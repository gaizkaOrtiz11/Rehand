package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.rehand.domain.TransactionType;

class WalletHistoryFilterTest {

    @Test
    void reversedDatesAreSwapped() {
        WalletHistoryFilter f = new WalletHistoryFilter(null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1));
        assertThat(f.getStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(f.getEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void pageLinkPrefixKeepsFilters() {
        WalletHistoryFilter f = new WalletHistoryFilter(TransactionType.REFUND, LocalDate.of(2026, 11, 1), null);
        assertThat(f.pageLinkPrefix()).isEqualTo("type=REFUND&from=2026-11-01&");
        assertThat(f.isActive()).isTrue();
        assertThat(new WalletHistoryFilter().pageLinkPrefix()).isEmpty();
        assertThat(new WalletHistoryFilter().isActive()).isFalse();
    }

    @Test
    void pageIsOneBased() {
        WalletHistoryFilter f = new WalletHistoryFilter();
        f.setPage(0);
        assertThat(f.getPageIndex()).isZero();
        f.setPage(3);
        assertThat(f.getPageIndex()).isEqualTo(2);
    }
}
