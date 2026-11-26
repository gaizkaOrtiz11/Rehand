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

import com.rehand.domain.Offer;
import com.rehand.domain.OfferStatus;

/**
 * Offers and counter-offers. "Latest offer of a negotiation" means an offer that no other offer
 * counters ({@code not exists ... c.parent = o}): every row of the offers lists is one negotiation.
 * The list and detail queries fetch listing, seller and buyer (open-in-view is off); the locking
 * queries fetch nothing, so only offer rows are locked.
 */
public interface OfferRepository extends JpaRepository<Offer, Long> {

    String LATEST = " not exists (select c.id from Offer c where c.parent = o) ";

    /** The offer with a row lock (SELECT ... FOR UPDATE). Every state change goes through it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);

    /** Only the listing id, so the listing can be locked before the offer (same lock order everywhere). */
    @Query("select o.listing.id from Offer o where o.id = :id")
    Optional<Long> findListingIdById(@Param("id") Long id);

    /** One offer with listing, seller and buyer. */
    @Query("select o from Offer o join fetch o.listing l join fetch l.seller join fetch o.buyer where o.id = :id")
    Optional<Offer> findDetailById(@Param("id") Long id);

    /** Every offer of one buyer on one listing, oldest first (the negotiation page rebuilds the chain from it). */
    @Query("select o from Offer o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where l.id = :listingId and o.buyer.id = :buyerId order by o.createdAt asc, o.id asc")
    List<Offer> findAllOfBuyerOnListing(@Param("listingId") Long listingId, @Param("buyerId") Long buyerId);

    /** Offers of one buyer on one listing in the given statuses, newest first. */
    @Query("select o from Offer o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where l.id = :listingId and o.buyer.id = :buyerId and o.status in :statuses "
            + "order by o.createdAt desc, o.id desc")
    List<Offer> findByBuyerOnListing(@Param("listingId") Long listingId, @Param("buyerId") Long buyerId,
                                     @Param("statuses") Collection<OfferStatus> statuses);

    /**
     * Offers on a listing in the given statuses except one, locked, so a concurrent withdraw or counter
     * waits and is re-checked afterwards. Buyers load lazily (for the notifications).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.listing.id = :listingId and o.status in :statuses and o.id <> :exceptId")
    List<Offer> findOnListingForUpdate(@Param("listingId") Long listingId,
                                       @Param("statuses") Collection<OfferStatus> statuses,
                                       @Param("exceptId") Long exceptId);

    long countByListingIdAndStatusIn(Long listingId, Collection<OfferStatus> statuses);

    /** Negotiations on the seller's listings (latest offer of each chain), the Pageable carries the sort. */
    @Query(value = "select o from Offer o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where l.seller.id = :userId and o.status in :statuses and" + LATEST,
            countQuery = "select count(o) from Offer o where o.listing.seller.id = :userId "
            + "and o.status in :statuses and" + LATEST)
    Page<Offer> findReceived(@Param("userId") Long userId,
                             @Param("statuses") Collection<OfferStatus> statuses,
                             Pageable pageable);

    /** Negotiations the member started as a buyer (latest offer of each chain). */
    @Query(value = "select o from Offer o join fetch o.listing l join fetch l.seller join fetch o.buyer "
            + "where o.buyer.id = :userId and o.status in :statuses and" + LATEST,
            countQuery = "select count(o) from Offer o where o.buyer.id = :userId "
            + "and o.status in :statuses and" + LATEST)
    Page<Offer> findSent(@Param("userId") Long userId,
                         @Param("statuses") Collection<OfferStatus> statuses,
                         Pageable pageable);

    /** Number of negotiations waiting for an answer from this user as seller (PENDING buyer offers). */
    @Query("select count(o) from Offer o where o.listing.seller.id = :userId "
            + "and o.status = com.rehand.domain.OfferStatus.PENDING and o.madeBy = com.rehand.domain.Party.BUYER")
    long countWaitingForSeller(@Param("userId") Long userId);

    /** Number of seller counters waiting for an answer from this user as buyer. */
    @Query("select count(o) from Offer o where o.buyer.id = :userId "
            + "and o.status = com.rehand.domain.OfferStatus.PENDING and o.madeBy = com.rehand.domain.Party.SELLER")
    long countWaitingForBuyer(@Param("userId") Long userId);

    /**
     * Ids of PENDING or ACCEPTED offers whose deadline has passed and that did not become an order
     * (an accepted offer that was checked out stays ACCEPTED for good). The scheduler handles them one by one.
     */
    @Query("select o.id from Offer o where o.status in :statuses and o.expiresAt <= :now "
            + "and not exists (select po.id from PurchaseOrder po where po.offerId = o.id) order by o.expiresAt")
    List<Long> findExpiredIds(@Param("statuses") Collection<OfferStatus> statuses, @Param("now") LocalDateTime now);
}
