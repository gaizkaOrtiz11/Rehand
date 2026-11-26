package com.rehand.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rehand.domain.Order;
import com.rehand.domain.OrderStatus;

/**
 * Orders. The list queries fetch the listing, its seller and the buyer in the same query
 * (open-in-view is off); the cover photos are initialised by the service (Listing.photos has
 * a BatchSize, so one extra query loads the photos of a whole page).
 *
 * The status filter is always bound: the service passes every status when no filter is chosen.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Purchases of a buyer, newest first (the Pageable carries the sort). */
    @Query(value = "select o from PurchaseOrder o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where o.buyer.id = :userId and o.status in :statuses",
            countQuery = "select count(o) from PurchaseOrder o where o.buyer.id = :userId and o.status in :statuses")
    Page<Order> findPurchases(@Param("userId") Long userId,
                              @Param("statuses") Collection<OrderStatus> statuses,
                              Pageable pageable);

    /** Sales of a seller (orders on listings the user owns), newest first. */
    @Query(value = "select o from PurchaseOrder o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where l.seller.id = :userId and o.status in :statuses",
            countQuery = "select count(o) from PurchaseOrder o where o.listing.seller.id = :userId and o.status in :statuses")
    Page<Order> findSales(@Param("userId") Long userId,
                          @Param("statuses") Collection<OrderStatus> statuses,
                          Pageable pageable);

    /** One order with listing, seller and buyer, for the detail page and every state change. */
    @Query("select o from PurchaseOrder o join fetch o.listing l join fetch l.seller join fetch o.buyer where o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);

    /**
     * The order with a row lock (SELECT ... FOR UPDATE): every state change goes through it, so for example
     * the buyer's "All good" and the scheduler cannot both pay the seller. Only the orders row is locked
     * (no join fetch, so no user rows are locked); listing and users load lazily in the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Ids of DELIVERED orders delivered before the given time (the scheduler completes them one by one). */
    @Query("select o.id from PurchaseOrder o where o.status = com.rehand.domain.OrderStatus.DELIVERED "
            + "and o.deliveredAt < :before order by o.deliveredAt")
    List<Long> findDeliveredBefore(@Param("before") LocalDateTime before);

    /** True when the listing has at least one order in one of the statuses. */
    boolean existsByListingIdAndStatusIn(Long listingId, Collection<OrderStatus> statuses);

    long countByBuyerIdAndStatusIn(Long buyerId, Collection<OrderStatus> statuses);

    /** True when an order (in any status, cancelled ones too) was placed with this offer: offer_id is UNIQUE. */
    boolean existsByOfferId(Long offerId);

    /** The ids among the given offer ids that already became an order. */
    @Query("select o.offerId from PurchaseOrder o where o.offerId in :offerIds")
    List<Long> findUsedOfferIds(@Param("offerIds") Collection<Long> offerIds);

    /** The order placed with this offer (for the "see your order" link), if any. */
    @Query("select o.id from PurchaseOrder o where o.offerId = :offerId")
    Optional<Long> findIdByOfferId(@Param("offerId") Long offerId);

    @Query("select count(o) from PurchaseOrder o where o.listing.seller.id = :userId and o.status in :statuses")
    long countSales(@Param("userId") Long userId, @Param("statuses") Collection<OrderStatus> statuses);
}
