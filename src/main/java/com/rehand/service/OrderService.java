package com.rehand.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.NotificationType;
import com.rehand.domain.Offer;
import com.rehand.domain.Order;
import com.rehand.domain.OrderStatus;
import com.rehand.domain.User;
import com.rehand.dto.CancelForm;
import com.rehand.dto.CheckoutForm;
import com.rehand.dto.CheckoutView;
import com.rehand.dto.OrderView;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.OrderRepository;
import com.rehand.repository.UserRepository;

/**
 * Checkout and order follow-up (module M5). Every public method runs in one transaction, so the
 * wallet movement ({@link WalletService} joins it), the order change, the listing status and the
 * notification commit together or not at all.
 *
 * Authorisation lives here, not only in the controller: buyer-only and seller-only actions are
 * checked against the order; admins may view every order and may cancel a PAID one.
 * State changes load the order with a row lock, so two concurrent actions are applied one after the other.
 */
@Service
@Transactional
public class OrderService {

    public static final int PAGE_SIZE = 10;

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Collection<OrderStatus> ALL_STATUSES = EnumSet.allOf(OrderStatus.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final OfferService offerService;
    private final TransactionTemplate perOrderTransaction;
    private final int autoCompleteHours;

    public OrderService(OrderRepository orderRepository,
                        ListingRepository listingRepository,
                        UserRepository userRepository,
                        WalletService walletService,
                        NotificationService notificationService,
                        OfferService offerService,
                        PlatformTransactionManager transactionManager,
                        @Value("${app.orders.auto-complete-hours:48}") int autoCompleteHours) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.offerService = offerService;
        this.perOrderTransaction = new TransactionTemplate(transactionManager);
        this.autoCompleteHours = autoCompleteHours;
    }

    // ================================================================ checkout

    /**
     * Data for the checkout page. Throws {@link BusinessRuleException} when this member cannot buy the
     * listing (own listing, not available, banned seller) and {@link NotFoundException} for an unknown,
     * hidden or removed listing.
     */
    @Transactional(readOnly = true)
    public CheckoutView checkoutView(Long buyerId, Long listingId) {
        return checkoutView(buyerId, listingId, null);
    }

    /**
     * Same, for checking out an accepted offer ({@code offerId} not null): the offer is validated and its
     * amount replaces the asking price. A RESERVED listing can only be checked out this way, by the buyer
     * who holds the accepted offer.
     */
    @Transactional(readOnly = true)
    public CheckoutView checkoutView(Long buyerId, Long listingId, Long offerId) {
        User buyer = requireUser(buyerId);
        Listing listing = listingRepository.findDetailById(listingId)
                .filter(l -> l.getStatus() != ListingStatus.HIDDEN && l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        Offer offer = checkoutOffer(listing, buyer, offerId);
        checkPurchasable(listing, buyer, offer != null);
        listing.getPhotos().size();
        return new CheckoutView(listing, buyer, priceOf(listing, offer), walletService.balanceOf(buyerId),
                WalletService.TOP_UP_MIN, offer == null ? null : offer.getId());
    }

    /**
     * Buy now. Locks the listing row first, so of two buyers who pay at the same moment only the first
     * one gets the item. Then: order PAID, wallet debit, listing SOLD, seller notified. If the wallet
     * refuses the payment the exception rolls everything back.
     */
    public Order placeOrder(Long buyerId, Long listingId, CheckoutForm form) {
        User buyer = requireUser(buyerId);
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .filter(l -> l.getStatus() != ListingStatus.HIDDEN && l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + listingId));
        Offer offer = checkoutOffer(listing, buyer, form.getOfferId());
        checkPurchasable(listing, buyer, offer != null);
        BigDecimal price = priceOf(listing, offer);
        Long offerId = offer == null ? null : offer.getId();

        Order order = orderRepository.save(new Order(listing, buyer, price, form.toAddress(), offerId));
        walletService.pay(buyer, order);
        listing.markSold();
        offerService.closeOpenOffers(listing, offerId, "the item was sold.");

        notificationService.notify(listing.getSeller(), NotificationType.ITEM_SOLD,
                buyer.getUsername() + " bought \"" + listing.getTitle() + "\" for " + euro(price)
                        + ". Ship it and add the tracking code.",
                link(order));
        return order;
    }

    /**
     * The price this buyer pays: the listing price, or the amount of the accepted offer {@code offerId}
     * (which must be ACCEPTED, not expired, of this buyer and listing, and not used for an order yet).
     */
    BigDecimal resolvePrice(Listing listing, User buyer, Long offerId) {
        return priceOf(listing, checkoutOffer(listing, buyer, offerId));
    }

    /** The validated accepted offer, or null for a plain buy now. */
    private Offer checkoutOffer(Listing listing, User buyer, Long offerId) {
        return offerService.requireCheckoutOffer(offerId, listing, buyer);
    }

    private static BigDecimal priceOf(Listing listing, Offer offer) {
        return offer == null ? listing.getPrice() : offer.getAmount();
    }

    /**
     * @param holdsReservation true when the buyer checks out a valid accepted offer of this listing: only then
     *                         may a RESERVED listing be bought
     */
    private void checkPurchasable(Listing listing, User buyer, boolean holdsReservation) {
        if (buyer.isBanned()) {
            throw new BusinessRuleException("This account cannot buy items.");
        }
        if (listing.isOwnedBy(buyer.getId())) {
            throw new BusinessRuleException("This is your own listing, so you cannot buy it.");
        }
        if (listing.getSeller().isBanned()) {
            throw new BusinessRuleException("This seller cannot sell items at the moment.");
        }
        if (!listing.canBePurchasedBy(buyer, holdsReservation)) {
            throw new BusinessRuleException(listing.getStatus() == ListingStatus.RESERVED
                    ? "This item is reserved for the buyer whose offer was accepted." : "This item is no longer available.");
        }
    }

    // ================================================================ follow-up

    /** Seller: PAID to SHIPPED with the carrier's tracking code. */
    public Order markShipped(Long sellerId, Long orderId, String trackingCode) {
        Order order = lock(orderId);
        if (!order.isSeller(sellerId)) {
            throw new BusinessRuleException("Only the seller can mark this order as shipped.");
        }
        order.ship(trackingCode);
        notificationService.notify(order.getBuyer(), NotificationType.ORDER_STATUS,
                "\"" + order.getListing().getTitle() + "\" is on its way. Tracking code: " + order.getTrackingCode() + ".",
                link(order));
        return order;
    }

    /** Buyer: SHIPPED to DELIVERED. */
    public Order confirmDelivery(Long buyerId, Long orderId) {
        Order order = lock(orderId);
        if (!order.isBuyer(buyerId)) {
            throw new BusinessRuleException("Only the buyer can confirm the delivery.");
        }
        order.confirmDelivery();
        notificationService.notify(order.getSeller(), NotificationType.ORDER_STATUS,
                order.getBuyer().getUsername() + " received \"" + order.getListing().getTitle()
                        + "\". The payment is released after their check, at the latest in " + autoCompleteHours + " hours.",
                link(order));
        return order;
    }

    /** Buyer clicks "All good": DELIVERED to COMPLETED and the seller is paid. */
    public Order complete(Long buyerId, Long orderId) {
        Order order = lock(orderId);
        if (!order.isBuyer(buyerId)) {
            throw new BusinessRuleException("Only the buyer can release the payment.");
        }
        completeAndPay(order, false);
        return order;
    }

    /**
     * Buyer, seller or an admin: PAID to CANCELLED. Full refund, the listing is for sale again and
     * the other party is told (with the reason, if one was given; it is not stored anywhere else).
     */
    public Order cancel(Long userId, Long orderId, String reason) {
        Order order = lock(orderId);
        User actor = requireUser(userId);
        boolean buyer = order.isBuyer(userId);
        boolean seller = order.isSeller(userId);
        if (!buyer && !seller && !actor.isAdmin()) {
            throw new BusinessRuleException("Only the buyer or the seller can cancel this order.");
        }
        String why = reason == null ? null : reason.trim();
        if (why != null && why.length() > CancelForm.REASON_MAX) {
            throw new BusinessRuleException("The reason can have at most " + CancelForm.REASON_MAX + " characters.");
        }

        order.cancel();
        walletService.refund(order);
        Listing listing = order.getListing();
        if (listing.getStatus() == ListingStatus.SOLD) {
            listing.reopen();
        }

        String title = "\"" + order.getListing().getTitle() + "\"";
        String suffix = why == null || why.isEmpty() ? "" : " Reason: " + why;
        if (buyer) {
            notificationService.notify(order.getSeller(), NotificationType.ORDER_STATUS,
                    order.getBuyer().getUsername() + " cancelled the order for " + title
                            + ". Your listing is for sale again." + suffix, link(order));
        } else if (seller) {
            notificationService.notify(order.getBuyer(), NotificationType.ORDER_STATUS,
                    "The seller cancelled your order for " + title + ". " + euro(order.getPrice())
                            + " was refunded to your wallet." + suffix, link(order));
        } else {
            notificationService.notify(order.getBuyer(), NotificationType.ORDER_STATUS,
                    "An administrator cancelled your order for " + title + ". " + euro(order.getPrice())
                            + " was refunded to your wallet." + suffix, link(order));
            notificationService.notify(order.getSeller(), NotificationType.ORDER_STATUS,
                    "An administrator cancelled the order for " + title + ". Your listing is for sale again." + suffix,
                    link(order));
        }
        return order;
    }

    // ================================================================ scheduler

    /**
     * Completes every DELIVERED order whose delivery is older than {@code app.orders.auto-complete-hours}
     * (default 48) and pays the seller. Each order runs in its own transaction, so one failure does not
     * stop the others. Called by {@link OrderScheduler}.
     *
     * @return number of orders completed
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int autoCompleteDelivered() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(autoCompleteHours);
        List<Long> ids = orderRepository.findDeliveredBefore(cutoff);
        int completed = 0;
        for (Long id : ids) {
            try {
                Boolean done = perOrderTransaction.execute(status -> autoCompleteOne(id, cutoff));
                if (Boolean.TRUE.equals(done)) {
                    completed++;
                }
            } catch (RuntimeException e) {
                log.warn("Auto-complete of order {} failed: {}", id, e.getMessage());
            }
        }
        if (!ids.isEmpty()) {
            log.info("Auto-complete: {} of {} delivered orders completed", completed, ids.size());
        }
        return completed;
    }

    /** Re-checks under the row lock: the buyer may have clicked "All good" in the meantime. */
    private boolean autoCompleteOne(Long orderId, LocalDateTime cutoff) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED
                || order.getDeliveredAt() == null || !order.getDeliveredAt().isBefore(cutoff)) {
            return false;
        }
        completeAndPay(order, true);
        return true;
    }

    private void completeAndPay(Order order, boolean automatic) {
        order.complete();
        walletService.releaseToSeller(order);
        notificationService.notify(order.getSeller(), NotificationType.ORDER_STATUS,
                euro(order.getPrice()) + " for \"" + order.getListing().getTitle() + "\" is now in your wallet."
                        + (automatic ? " The payment was released automatically " + autoCompleteHours + " hours after delivery." : ""),
                link(order));
        if (automatic) {
            notificationService.notify(order.getBuyer(), NotificationType.ORDER_STATUS,
                    "Your order for \"" + order.getListing().getTitle() + "\" was completed automatically.", link(order));
        }
    }

    // ================================================================ reading

    /** The detail page: visible to the buyer, the seller and admins; everyone else gets a 404. */
    @Transactional(readOnly = true)
    public OrderView view(Long viewerId, Long orderId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        boolean admin = requireUser(viewerId).isAdmin();
        if (!order.isParty(viewerId) && !admin) {
            throw new NotFoundException("Order not found: " + orderId);
        }
        order.getListing().getPhotos().size();
        return new OrderView(order, viewerId, admin, autoCompleteHours);
    }

    /** What the member bought, newest first. {@code status} null means every status. */
    @Transactional(readOnly = true)
    public Page<Order> purchases(Long userId, OrderStatus status, int pageIndex) {
        Page<Order> page = orderRepository.findPurchases(userId, statuses(status), pageable(pageIndex));
        initPhotos(page);
        return page;
    }

    /** What the member sold, newest first. {@code status} null means every status. */
    @Transactional(readOnly = true)
    public Page<Order> sales(Long userId, OrderStatus status, int pageIndex) {
        Page<Order> page = orderRepository.findSales(userId, statuses(status), pageable(pageIndex));
        initPhotos(page);
        return page;
    }

    /** Number of purchases and sales that still need something to happen (PAID, SHIPPED, DELIVERED). */
    @Transactional(readOnly = true)
    public long[] openCounts(Long userId) {
        Collection<OrderStatus> open = EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        return new long[] {
            orderRepository.countByBuyerIdAndStatusIn(userId, open),
            orderRepository.countSales(userId, open)
        };
    }

    public int getAutoCompleteHours() {
        return autoCompleteHours;
    }

    // ================================================================ helpers

    private Order lock(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessRuleException("Log in first.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private static Collection<OrderStatus> statuses(OrderStatus status) {
        return status == null ? ALL_STATUSES : EnumSet.of(status);
    }

    private static Pageable pageable(int pageIndex) {
        return PageRequest.of(Math.max(pageIndex, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private static void initPhotos(Page<Order> page) {
        for (Order o : page.getContent()) {
            o.getListing().getPhotos().size();
        }
    }

    private static String link(Order order) {
        return "/orders/" + order.getId();
    }

    static String euro(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
