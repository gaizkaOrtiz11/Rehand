package com.rehand.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Every balance change of the simulated wallet. Money flow without an escrow table: {@link #pay} debits the
 * buyer (the amount then lives on {@code orders.price}), {@link #releaseToSeller} credits the seller when the
 * order completes and {@link #refund} credits the buyer when it is cancelled.
 *
 * <p>All public methods are {@code @Transactional} with the default propagation (REQUIRED): called from
 * {@code OrderService} they join its transaction, so the order change and the wallet change commit or roll back
 * together. Each method locks exactly one wallet row ({@code SELECT ... FOR UPDATE}), which rules out deadlocks
 * between two wallets.</p>
 */
@Service
public class WalletService {

    public static final BigDecimal TOP_UP_MIN = new BigDecimal("5.00");
    public static final BigDecimal TOP_UP_MAX = new BigDecimal("1000.00");
    public static final BigDecimal WITHDRAW_MIN = new BigDecimal("1.00");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository transactionRepository,
                         UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------ order money

    /**
     * Debits the order price from the buyer as PAYMENT linked to the order.
     *
     * @throws BusinessRuleException "Your wallet balance is too low." when the balance does not cover the price
     */
    @Transactional
    public void pay(User buyer, Order order) {
        Wallet wallet = lockWallet(buyer);
        transactionRepository.save(wallet.debit(order.getPrice(), TransactionType.PAYMENT, order));
    }

    /** Credits the order price to the seller as SALE_CREDIT (order COMPLETED). */
    @Transactional
    public void releaseToSeller(Order order) {
        Wallet wallet = lockWallet(order.getSeller());
        transactionRepository.save(wallet.credit(order.getPrice(), TransactionType.SALE_CREDIT, order));
    }

    /** Gives the order price back to the buyer as REFUND (order CANCELLED). */
    @Transactional
    public void refund(Order order) {
        Wallet wallet = lockWallet(order.getBuyer());
        transactionRepository.save(wallet.credit(order.getPrice(), TransactionType.REFUND, order));
    }

    // ------------------------------------------------------- top-up, withdraw

    /** Simulated card top-up between 5.00 and 1000.00 euros. */
    @Transactional
    public void topUp(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(TOP_UP_MIN) < 0 || amount.compareTo(TOP_UP_MAX) > 0) {
            throw new BusinessRuleException("A top-up must be between 5.00 and 1000.00 euros.");
        }
        Wallet wallet = lockWallet(userId);
        transactionRepository.save(wallet.credit(amount, TransactionType.TOP_UP, null));
    }

    /**
     * Simulated bank transfer of at least 1.00 euro and at most the balance.
     *
     * @throws BusinessRuleException "Your wallet balance is too low." when the amount is above the balance
     */
    @Transactional
    public void withdraw(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(WITHDRAW_MIN) < 0) {
            throw new BusinessRuleException("The minimum withdrawal is 1.00 euro.");
        }
        Wallet wallet = lockWallet(userId);
        transactionRepository.save(wallet.debit(amount, TransactionType.WITHDRAWAL, null));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /** Ledger rows of the user, newest first, filtered by type and an inclusive date range. */
    @Transactional(readOnly = true)
    public Page<WalletTransaction> history(Long userId, WalletHistoryFilter filter, Pageable pageable) {
        WalletHistoryFilter f = filter == null ? new WalletHistoryFilter() : filter;
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return transactionRepository.findAll(historySpec(userId, f), sorted);
    }

    /** Totals per type in one calendar month (money in, money out). */
    @Transactional(readOnly = true)
    public WalletSummary monthSummary(Long userId, YearMonth month) {
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        Map<TransactionType, BigDecimal> totals = new EnumMap<>(TransactionType.class);
        for (WalletTransactionRepository.TypeTotal row : transactionRepository.totalsByType(userId, from, to)) {
            if (row.getType() != null && row.getTotal() != null) {
                totals.put(row.getType(), row.getTotal());
            }
        }
        return new WalletSummary(totals);
    }

    static Specification<WalletTransaction> historySpec(Long userId, WalletHistoryFilter f) {
        TransactionType type = f.getType();
        LocalDate start = f.getStart();
        LocalDate end = f.getEnd();
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("wallet").get("user").get("id"), userId));
            if (type != null) {
                where.add(cb.equal(root.get("type"), type));
            }
            if (start != null) {
                where.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), start.atStartOfDay()));
            }
            if (end != null) {
                where.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), end.plusDays(1).atStartOfDay()));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    // ---------------------------------------------------------------- locking

    private Wallet lockWallet(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A saved user is required.");
        }
        return walletRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> createWallet(user));
    }

    private Wallet lockWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> createWallet(userRepository.findById(userId)
                        .orElseThrow(() -> new NotFoundException("User " + userId + " not found"))));
    }

    /** Should not happen (registration creates the wallet), but a missing row must not block a payment. */
    private Wallet createWallet(User user) {
        return walletRepository.saveAndFlush(new Wallet(user, BigDecimal.ZERO.setScale(2)));
    }
}
