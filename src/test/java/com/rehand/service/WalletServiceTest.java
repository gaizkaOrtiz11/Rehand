package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.rehand.domain.Order;
import com.rehand.domain.TransactionType;
import com.rehand.domain.User;
import com.rehand.domain.Wallet;
import com.rehand.domain.WalletTransaction;
import com.rehand.dto.WalletHistoryFilter;
import com.rehand.dto.WalletSummary;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.UserRepository;
import com.rehand.repository.WalletRepository;
import com.rehand.repository.WalletTransactionRepository;

/** Pure unit test: repositories and the order are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private WalletService service;

    private User buyer;
    private User seller;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository, transactionRepository, userRepository);
        buyer = user(2L);
        seller = user(3L);
    }

    private static User user(long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername("user" + id);
        return u;
    }

    private Wallet walletOf(User owner, String balance) {
        Wallet w = new Wallet(owner, new BigDecimal(balance));
        when(walletRepository.findByUserIdForUpdate(owner.getId())).thenReturn(Optional.of(w));
        return w;
    }

    private Order order(String price) {
        Order order = mock(Order.class);
        when(order.getPrice()).thenReturn(new BigDecimal(price));
        return order;
    }

    private WalletTransaction savedTransaction() {
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    // -------------------------------------------------------------- pay

    @Test
    void payDebitsBuyerAndLinksOrder() {
        Wallet wallet = walletOf(buyer, "100.00");
        Order order = order("40.00");

        service.pay(buyer, order);

        WalletTransaction tx = savedTransaction();
        assertThat(tx.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(tx.getAmount()).isEqualByComparingTo("40.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(tx.getOrder()).isSameAs(order);
        assertThat(tx.getWallet()).isSameAs(wallet);
        assertThat(wallet.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void payWithTooLowBalanceThrowsAndSavesNothing() {
        Wallet wallet = walletOf(buyer, "39.99");
        Order order = order("40.00");

        assertThatThrownBy(() -> service.pay(buyer, order))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Your wallet balance is too low.");
        verify(transactionRepository, never()).save(any());
        assertThat(wallet.getBalance()).isEqualByComparingTo("39.99");
    }

    @Test
    void payCreatesMissingWalletLazily() {
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        Order order = order("5.00");

        assertThatThrownBy(() -> service.pay(buyer, order)).isInstanceOf(BusinessRuleException.class);
        ArgumentCaptor<Wallet> created = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).saveAndFlush(created.capture());
        assertThat(created.getValue().getUser()).isSameAs(buyer);
        assertThat(created.getValue().getBalance()).isEqualByComparingTo("0.00");
    }

    // ----------------------------------------------------- release, refund

    @Test
    void releaseToSellerCreditsSaleCredit() {
        Wallet wallet = walletOf(seller, "10.00");
        Order order = order("40.00");
        when(order.getSeller()).thenReturn(seller);

        service.releaseToSeller(order);

        WalletTransaction tx = savedTransaction();
        assertThat(tx.getType()).isEqualTo(TransactionType.SALE_CREDIT);
        assertThat(tx.getAmount()).isEqualByComparingTo("40.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("50.00");
        assertThat(tx.getOrder()).isSameAs(order);
        assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");
        verify(walletRepository, never()).findByUserIdForUpdate(2L);
    }

    @Test
    void refundCreditsBuyer() {
        Wallet wallet = walletOf(buyer, "60.00");
        Order order = order("40.00");
        when(order.getBuyer()).thenReturn(buyer);

        service.refund(order);

        WalletTransaction tx = savedTransaction();
        assertThat(tx.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(tx.getOrder()).isSameAs(order);
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
    }

    // ---------------------------------------------------- top-up, withdraw

    @Test
    void topUpCreditsWithoutOrder() {
        walletOf(buyer, "0.00");

        service.topUp(2L, new BigDecimal("25.00"));

        WalletTransaction tx = savedTransaction();
        assertThat(tx.getType()).isEqualTo(TransactionType.TOP_UP);
        assertThat(tx.getAmount()).isEqualByComparingTo("25.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("25.00");
        assertThat(tx.getOrder()).isNull();
    }

    @Test
    void topUpOutsideLimitsIsRefused() {
        assertThatThrownBy(() -> service.topUp(2L, new BigDecimal("4.99"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.topUp(2L, new BigDecimal("1000.01"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.topUp(2L, null)).isInstanceOf(BusinessRuleException.class);
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void topUpForUnknownUserWithoutWalletIsNotFound() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.topUp(99L, new BigDecimal("10.00"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawDebitsWithoutOrder() {
        walletOf(buyer, "75.50");

        service.withdraw(2L, new BigDecimal("75.50"));

        WalletTransaction tx = savedTransaction();
        assertThat(tx.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("0.00");
        assertThat(tx.getOrder()).isNull();
    }

    @Test
    void withdrawMoreThanBalanceIsRefused() {
        walletOf(buyer, "20.00");
        assertThatThrownBy(() -> service.withdraw(2L, new BigDecimal("20.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Your wallet balance is too low.");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawBelowMinimumIsRefused() {
        assertThatThrownBy(() -> service.withdraw(2L, new BigDecimal("0.99"))).isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- reads

    @Test
    void balanceOfDefaultsToZero() {
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.empty());
        assertThat(service.balanceOf(5L)).isEqualByComparingTo("0.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void historySortsNewestFirst() {
        Page<WalletTransaction> empty = new PageImpl<>(List.of());
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        service.history(2L, new WalletHistoryFilter(), PageRequest.of(1, 12));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void monthSummarySplitsMoneyInAndOut() {
        YearMonth month = YearMonth.of(2026, 11);
        when(transactionRepository.totalsByType(eq(2L), eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0))))
                .thenReturn(List.of(total(TransactionType.TOP_UP, "50.00"), total(TransactionType.SALE_CREDIT, "20.00"),
                        total(TransactionType.PAYMENT, "30.00")));

        WalletSummary summary = service.monthSummary(2L, month);

        assertThat(summary.getMoneyIn()).isEqualByComparingTo("70.00");
        assertThat(summary.getMoneyOut()).isEqualByComparingTo("30.00");
        assertThat(summary.total(TransactionType.WITHDRAWAL)).isEqualByComparingTo("0.00");
    }

    private static WalletTransactionRepository.TypeTotal total(TransactionType type, String amount) {
        return new WalletTransactionRepository.TypeTotal() {
            @Override
            public TransactionType getType() {
                return type;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(amount);
            }
        };
    }
}
