package com.rehand.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.NotificationType;
import com.rehand.domain.Offer;
import com.rehand.domain.OfferStatus;
import com.rehand.domain.Party;
import com.rehand.domain.User;
import com.rehand.dto.NegotiationView;
import com.rehand.dto.OfferFilter;
import com.rehand.dto.OfferPanel;
import com.rehand.dto.OfferRow;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.OfferRepository;
import com.rehand.repository.OrderRepository;
import com.rehand.repository.UserRepository;

/**
 * Offers and counter-offers (module M4).
 *
 * <ul>
 *   <li>A buyer offers below the asking price; at most one open negotiation (PENDING, or ACCEPTED and not yet
 *       checked out) per buyer and listing. An offer can be answered for 48 hours.</li>
 *   <li>The party who must answer (the seller for a buyer offer, the buyer for a seller counter) accepts,
 *       declines or counters. A counter creates a new PENDING offer from the other party.</li>
 *   <li>Accepting reserves the listing for the buyer for 24 hours and declines every other open offer
 *       on it. {@link OrderService} checks the accepted offer at checkout.</li>
 *   <li>The scheduler expires stale offers and puts the listing of an expired acceptance back on sale.</li>
 * </ul>
 *
 * Locks: the listing row is locked before the offer row (accept, scheduler, make offer), so two transactions
 * never wait for each other in opposite order. Counter also locks the listing first (its insert needs the
 * listing row). Reject and withdraw only lock the offer row; the status is re-checked under the lock.
 */
@Service
@Transactional
public class OfferService {

    public static final int PAGE_SIZE = 12;

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final Collection<OfferStatus> OPEN = EnumSet.of(OfferStatus.PENDING, OfferStatus.ACCEPTED);
    private static final Collection<OfferStatus> PENDING = EnumSet.of(OfferStatus.PENDING);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM 'at' HH:mm", Locale.ENGLISH);

    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate perOfferTransaction;

    public OfferService(OfferRepository offerRepository,
                        ListingRepository listingRepository,
                        OrderRepository orderRepository,
                        UserRepository userRepository,
                        NotificationService notificationService,
                        PlatformTransactionManager transactionManager) {
        this.offerRepository = offerRepository;
        this.listingRepository = listingRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.perOfferTransaction = new TransactionTemplate(transactionManager);
    }

    // ================================================================ make an offer

    /** Buyer: a new offer below the asking price. The listing row is locked, so a double submit cannot create two. */
    public Offer makeOffer(Long buyerId, Long listingId, BigDecimal amount) {
        User buyer = requireUser(buyerId);
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .filter(l -> l.getStatus() != ListingStatus.HIDDEN && l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        if (buyer.isBanned()) {
            throw new BusinessRuleException("This account cannot make offers.");
        }
        if (listing.isOwnedBy(buyerId)) {
            throw new BusinessRuleException("This is your own listing, so you cannot make an offer on it.");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("This item is not available for offers.");
        }
        if (listing.getSeller().isBanned()) {
            throw new BusinessRuleException("This seller cannot sell items at the moment.");
        }
        BigDecimal value = checkAmount(amount, listing);
        LocalDateTime now = LocalDateTime.now();
        if (openNegotiation(listingId, buyerId, now) != null) {
            throw new BusinessRuleException("You already have an open offer on this item. Answer or withdraw it first.");
        }

        Offer offer = offerRepository.save(Offer.fromBuyer(listing, buyer, value, now));
        notificationService.notify(listing.getSeller(), NotificationType.OFFER_RECEIVED,
                buyer.getUsername() + " offered " + euro(value) + " for " + quoted(listing)
                        + " (asking " + euro(listing.getPrice()) + "). Answer within " + Offer.ANSWER_HOURS + " hours.",
                link(offer));
        return offer;
    }

    // ================================================================ respond

    /**
     * The responder accepts. Locks the listing, then the offer. The listing becomes RESERVED for the buyer,
     * who has 24 hours to check out, and every other open offer on it is declined.
     */
    public Offer accept(Long userId, Long offerId) {
        Long listingId = offerRepository.findListingIdById(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found: " + offerId));
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        LocalDateTime now = LocalDateTime.now();
        Offer offer = lockForAnswer(userId, offerId, now);
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("This item is no longer available.");
        }

        offer.accept(now);
        listing.reserve();
        closeOpenOffers(listing, offer.getId(), "the seller accepted another offer.");

        String deadline = WHEN.format(offer.getExpiresAt());
        if (offer.getMadeBy() == Party.BUYER) {
            notificationService.notify(offer.getBuyer(), NotificationType.OFFER_ANSWERED,
                    "The seller accepted your offer of " + euro(offer.getAmount()) + " for " + quoted(listing)
                            + ". Check out before " + deadline + ".", link(offer));
        } else {
            notificationService.notify(listing.getSeller(), NotificationType.OFFER_ANSWERED,
                    offer.getBuyer().getUsername() + " accepted your counter-offer of " + euro(offer.getAmount())
                            + " for " + quoted(listing) + ". The item is reserved for them until " + deadline + ".",
                    link(offer));
        }
        return offer;
    }

    /** The responder declines. The negotiation is closed. */
    public Offer reject(Long userId, Long offerId) {
        Offer offer = lockForAnswer(userId, offerId, LocalDateTime.now());
        offer.reject();
        User maker = offer.getMaker();
        String who = offer.getMadeBy() == Party.BUYER ? "The seller" : offer.getBuyer().getUsername();
        notificationService.notify(maker, NotificationType.OFFER_ANSWERED,
                who + " declined your offer of " + euro(offer.getAmount()) + " for " + quoted(offer.getListing()) + ".",
                link(offer));
        return offer;
    }

    /**
     * The responder counters with another amount (above zero, below the asking price, different from the
     * current one). Returns the new PENDING offer, which the other party must answer within 48 hours.
     */
    public Offer counter(Long userId, Long offerId, BigDecimal amount) {
        // The insert of the new offer needs a shared lock on the listing row (foreign key), so the listing
        // is locked first here too: otherwise a concurrent accept (listing, then offers) could deadlock with it.
        Long listingId = offerRepository.findListingIdById(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found: " + offerId));
        listingRepository.findByIdForUpdate(listingId);
        LocalDateTime now = LocalDateTime.now();
        Offer offer = lockForAnswer(userId, offerId, now);
        Listing listing = offer.getListing();
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("This item is no longer available.");
        }
        BigDecimal value = checkAmount(amount, listing);
        Offer next = offerRepository.save(offer.counter(value, now));

        User actor = offer.userOf(offer.responder());
        notificationService.notify(next.userOf(next.responder()), NotificationType.OFFER_RECEIVED,
                actor.getUsername() + " countered with " + euro(value) + " for " + quoted(listing)
                        + ". Answer within " + Offer.ANSWER_HOURS + " hours.",
                link(next));
        return next;
    }

    /** The party who made a PENDING offer takes it back. The negotiation is closed. */
    public Offer withdraw(Long userId, Long offerId) {
        Offer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found: " + offerId));
        Party party = offer.partyOf(userId);
        if (party == null) {
            throw new NotFoundException("Offer not found: " + offerId);
        }
        if (party != offer.getMadeBy()) {
            throw new BusinessRuleException("Only the member who made this offer can withdraw it.");
        }
        offer.withdraw();
        notificationService.notify(offer.userOf(offer.responder()), NotificationType.OFFER_ANSWERED,
                offer.getMaker().getUsername() + " withdrew the offer of " + euro(offer.getAmount()) + " for "
                        + quoted(offer.getListing()) + ".",
                link(offer));
        return offer;
    }

    /** Loads the offer with a row lock and checks that this user must answer it now. */
    private Offer lockForAnswer(Long userId, Long offerId, LocalDateTime now) {
        Offer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found: " + offerId));
        Party party = offer.partyOf(userId);
        if (party == null) {
            throw new NotFoundException("Offer not found: " + offerId);
        }
        if (!offer.isPending()) {
            throw new BusinessRuleException("This offer has already been answered.");
        }
        if (party != offer.responder()) {
            throw new BusinessRuleException("It is not your turn: the other member has to answer this offer.");
        }
        if (offer.isExpired(now)) {
            throw new BusinessRuleException("This offer has expired.");
        }
        return offer;
    }

    // ================================================================ used by other services

    /**
     * Declines every PENDING offer on the listing except {@code exceptOfferId} (may be null) and tells their
     * buyers why. The offers are locked first. Called when an offer is accepted, when the item is sold and
     * when the owner hides or deletes the listing.
     *
     * @param why end of the sentence "Your offer on "..." was closed: ", for example "the item was sold."
     * @return number of offers declined
     */
    public int closeOpenOffers(Listing listing, Long exceptOfferId, String why) {
        List<Offer> open = offerRepository.findOnListingForUpdate(listing.getId(), PENDING,
                exceptOfferId == null ? Long.valueOf(-1L) : exceptOfferId);
        int closed = 0;
        for (Offer o : open) {
            if (!o.isPending()) {
                continue;
            }
            o.reject();
            closed++;
            notificationService.notify(o.getBuyer(), NotificationType.OFFER_ANSWERED,
                    "Your offer on " + quoted(listing) + " was closed: " + why, link(o));
        }
        return closed;
    }

    /**
     * The accepted offer a buyer wants to check out. It must exist, belong to this buyer and listing, be
     * ACCEPTED, not expired and not used for an order yet (orders.offer_id is unique).
     *
     * @return the offer, or null when {@code offerId} is null (plain buy now)
     */
    public Offer requireCheckoutOffer(Long offerId, Listing listing, User buyer) {
        if (offerId == null) {
            return null;
        }
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new BusinessRuleException("This offer does not exist."));
        if (!listing.getId().equals(offer.getListing().getId()) || buyer == null || !offer.isBuyer(buyer.getId())) {
            throw new BusinessRuleException("This offer cannot be used for this purchase.");
        }
        if (!offer.isAccepted()) {
            throw new BusinessRuleException("This offer has not been accepted, so you cannot check out with it.");
        }
        if (offer.isExpired(LocalDateTime.now())) {
            throw new BusinessRuleException("The " + Offer.CHECKOUT_HOURS + " hours to check out this offer have passed.");
        }
        if (orderRepository.existsByOfferId(offerId)) {
            throw new BusinessRuleException("This offer has already been used for an order.");
        }
        return offer;
    }

    // ================================================================ scheduler

    /**
     * The "Scheduler" actor: expires PENDING offers past their answer deadline and ACCEPTED offers whose
     * 24 hours to check out have passed (the listing is then for sale again). Runs every
     * {@code app.offers.expiry-check-ms} (default 300000, five minutes). Each offer runs in its own
     * transaction, so one failure does not stop the others.
     *
     * @return number of offers expired
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int expireStaleOffers() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = offerRepository.findExpiredIds(OPEN, now);
        int pending = 0;
        int accepted = 0;
        for (Long id : ids) {
            try {
                Integer result = perOfferTransaction.execute(status -> expireOne(id, now));
                if (result != null && result == 1) {
                    pending++;
                } else if (result != null && result == 2) {
                    accepted++;
                }
            } catch (RuntimeException e) {
                log.warn("Expiry of offer {} failed: {}", id, e.getMessage());
            }
        }
        if (!ids.isEmpty()) {
            log.info("Offer expiry: {} pending and {} accepted offers expired ({} candidates)", pending, accepted, ids.size());
        }
        return pending + accepted;
    }

    /**
     * The scheduled entry point (void, as {@code @Scheduled} expects). It runs without a surrounding
     * transaction, so every offer gets its own one inside {@link #expireStaleOffers()}.
     */
    @Scheduled(fixedDelayString = "${app.offers.expiry-check-ms:300000}",
               initialDelayString = "${app.offers.expiry-initial-delay-ms:90000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void scheduledExpiry() {
        expireStaleOffers();
    }

    /** Locks the listing, then the offer, and re-checks. Returns 0 (nothing done), 1 (pending) or 2 (accepted). */
    private int expireOne(Long offerId, LocalDateTime now) {
        Long listingId = offerRepository.findListingIdById(offerId).orElse(null);
        if (listingId == null) {
            return 0;
        }
        Listing listing = listingRepository.findByIdForUpdate(listingId).orElse(null);
        Offer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (offer == null || listing == null || !offer.isExpired(now)
                || (!offer.isPending() && !offer.isAccepted())) {
            return 0;
        }
        boolean wasAccepted = offer.isAccepted();
        if (wasAccepted && orderRepository.existsByOfferId(offerId)) {
            return 0; // checked out in the meantime: the offer stays ACCEPTED
        }
        offer.expire();
        if (wasAccepted) {
            if (listing.getStatus() == ListingStatus.RESERVED) {
                listing.releaseReservation();
            }
            notificationService.notify(offer.getBuyer(), NotificationType.OFFER_ANSWERED,
                    "Your accepted offer on " + quoted(listing) + " expired because the checkout did not happen within "
                            + Offer.CHECKOUT_HOURS + " hours.", link(offer));
            notificationService.notify(listing.getSeller(), NotificationType.OFFER_ANSWERED,
                    offer.getBuyer().getUsername() + " did not check out " + quoted(listing)
                            + " in time. The reservation ended and the item is for sale again.", link(offer));
            return 2;
        }
        String text = offer.getMadeBy() == Party.BUYER
                ? "Your offer of " + euro(offer.getAmount()) + " on " + quoted(listing) + " expired without an answer."
                : "The seller's counter-offer of " + euro(offer.getAmount()) + " on " + quoted(listing) + " expired.";
        notificationService.notify(offer.getBuyer(), NotificationType.OFFER_ANSWERED, text, link(offer));
        return 1;
    }

    // ================================================================ reading

    /** The offer part of the listing page for this viewer (null viewer: a guest). */
    @Transactional(readOnly = true)
    public OfferPanel panel(Listing listing, Long viewerId) {
        LocalDateTime now = LocalDateTime.now();
        if (viewerId == null || listing == null) {
            return OfferPanel.anonymous();
        }
        if (listing.isOwnedBy(viewerId)) {
            long count = offerRepository.countByListingIdAndStatusIn(listing.getId(), PENDING);
            return new OfferPanel(true, true, listing, count, null, now);
        }
        return new OfferPanel(true, false, listing, 0, openNegotiation(listing.getId(), viewerId, now), now);
    }

    /**
     * Latest offer of the buyer's open negotiation on the listing: PENDING and not expired, or ACCEPTED,
     * not expired and not used for an order. Null when there is none.
     */
    Offer openNegotiation(Long listingId, Long buyerId, LocalDateTime now) {
        for (Offer o : offerRepository.findByBuyerOnListing(listingId, buyerId, OPEN)) {
            if (o.isExpired(now)) {
                continue;
            }
            if (o.isPending() || (o.isAccepted() && !orderRepository.existsByOfferId(o.getId()))) {
                return o;
            }
        }
        return null;
    }

    /** One page of negotiations (latest offer of each chain), newest first. */
    @Transactional(readOnly = true)
    public Page<OfferRow> list(Long userId, boolean received, OfferFilter filter, int pageIndex) {
        OfferFilter f = filter == null ? OfferFilter.ALL : filter;
        Pageable pageable = PageRequest.of(Math.max(pageIndex, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Offer> page = received
                ? offerRepository.findReceived(userId, f.getStatuses(), pageable)
                : offerRepository.findSent(userId, f.getStatuses(), pageable);

        List<Long> acceptedIds = new ArrayList<>();
        for (Offer o : page.getContent()) {
            o.getListing().getPhotos().size();
            if (o.isAccepted()) {
                acceptedIds.add(o.getId());
            }
        }
        Set<Long> used = acceptedIds.isEmpty() ? Set.of() : new HashSet<>(orderRepository.findUsedOfferIds(acceptedIds));
        LocalDateTime now = LocalDateTime.now();
        Party viewer = received ? Party.SELLER : Party.BUYER;
        List<OfferRow> rows = new ArrayList<>();
        for (Offer o : page.getContent()) {
            rows.add(new OfferRow(o, viewer, used.contains(o.getId()), now));
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /** Negotiations waiting for this user's answer: {as seller, as buyer} (the counts on the tabs). */
    @Transactional(readOnly = true)
    public long[] waitingCounts(Long userId) {
        return new long[] {
            offerRepository.countWaitingForSeller(userId),
            offerRepository.countWaitingForBuyer(userId)
        };
    }

    /**
     * The negotiation page for any offer of the chain. Visible to the buyer, the seller and admins;
     * everyone else gets a 404.
     */
    @Transactional(readOnly = true)
    public NegotiationView view(Long viewerId, Long offerId) {
        Offer offer = offerRepository.findDetailById(offerId)
                .orElseThrow(() -> new NotFoundException("Offer not found: " + offerId));
        boolean admin = requireUser(viewerId).isAdmin();
        if (!offer.isParty(viewerId) && !admin) {
            throw new NotFoundException("Offer not found: " + offerId);
        }
        List<Offer> all = offerRepository.findAllOfBuyerOnListing(offer.getListing().getId(), offer.getBuyer().getId());
        List<Offer> chain = chainOf(offer, all);
        Offer latest = chain.get(chain.size() - 1);
        latest.getListing().getPhotos().size();
        Long orderId = latest.isAccepted() ? orderRepository.findIdByOfferId(latest.getId()).orElse(null) : null;
        return new NegotiationView(chain, viewerId, admin, orderId, LocalDateTime.now());
    }

    /**
     * Rebuilds the chain that contains {@code offer} from the offers of the same buyer on the same listing:
     * up through the parent links to the first offer, then down to the latest counter.
     */
    static List<Offer> chainOf(Offer offer, List<Offer> all) {
        Map<Long, Offer> byId = new HashMap<>();
        Map<Long, Offer> childOf = new HashMap<>();
        for (Offer o : all) {
            byId.put(o.getId(), o);
        }
        byId.putIfAbsent(offer.getId(), offer);
        for (Offer o : byId.values()) {
            if (o.getParent() != null) {
                childOf.put(o.getParent().getId(), o);
            }
        }
        Offer first = byId.get(offer.getId());
        Set<Long> seen = new HashSet<>();
        while (first.getParent() != null && byId.containsKey(first.getParent().getId()) && seen.add(first.getId())) {
            first = byId.get(first.getParent().getId());
        }
        List<Offer> chain = new ArrayList<>();
        seen.clear();
        for (Offer o = first; o != null && seen.add(o.getId()); o = childOf.get(o.getId())) {
            chain.add(o);
        }
        return chain;
    }

    // ================================================================ helpers

    /** Above zero, at most two decimals, below the asking price. Returns the amount with two decimals. */
    static BigDecimal checkAmount(BigDecimal amount, Listing listing) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Enter an amount above zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Use euros with at most two decimals, for example 25.50.");
        }
        if (amount.compareTo(listing.getPrice()) >= 0) {
            throw new BusinessRuleException("An offer must be below the asking price of " + euro(listing.getPrice())
                    + ". To pay the full price, use Buy now.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessRuleException("Log in first.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private static String quoted(Listing listing) {
        return "\"" + listing.getTitle() + "\"";
    }

    private static String link(Offer offer) {
        return "/offers/" + offer.getId();
    }

    private static String euro(BigDecimal amount) {
        return OrderService.euro(amount);
    }
}
