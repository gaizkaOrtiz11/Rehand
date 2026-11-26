package com.rehand.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rehand.domain.TransactionType;
import com.rehand.domain.WalletTransaction;

/** Ledger rows. The filtered history uses {@link JpaSpecificationExecutor} (see WalletService.history). */
public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long>, JpaSpecificationExecutor<WalletTransaction> {

    /** Sum of amounts per type for one user's wallet in {@code [from, to)}. */
    @Query("select t.type as type, sum(t.amount) as total from WalletTransaction t "
            + "where t.wallet.user.id = :userId and t.createdAt >= :from and t.createdAt < :to "
            + "group by t.type")
    List<TypeTotal> totalsByType(@Param("userId") Long userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /** Projection row of {@link #totalsByType}. */
    interface TypeTotal {

        TransactionType getType();

        BigDecimal getTotal();
    }
}
