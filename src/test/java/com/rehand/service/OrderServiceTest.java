package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.NotificationType;
import com.rehand.domain.Offer;
import com.rehand.domain.OfferStatus;
import com.rehand.domain.Order;
import com.rehand.domain.OrderStatus;
import com.rehand.domain.Role;
import com.rehand.domain.ShippingAddress;
import com.rehand.domain.User;
import com.rehand.domain.UserStatus;
import com.rehand.dto.CheckoutForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.OfferRepository;
import com.rehand.repository.OrderRepository;
import com.rehand.repository.UserRepository;

/** Plain JUnit and Mockito: the order rules of {@link OrderService} without Spring or a database. */
class OrderServiceTest {

    private static final long SELLER_ID = 2L;
    private static final long BUYER_ID = 3L;
    private static final long STRANGER_ID = 9L;
    private static final long ADMIN_ID = 1L;
    private static final long LISTING_ID = 1015L;
    private static final long ORDER_ID = 5002L;

    private OrderRepository orders;
    private ListingRepository listings;
    private UserRepository users;
    private WalletService wallet;
    private NotificationService notifications;
    private OfferRepository offers;
    private OrderService service;

    private User seller;
    private User buyer;
    private User admin;
    private Listing listing;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        listings = mock(ListingRepository.class);
        users = mock(UserRepository.class);
        wallet = mock(WalletService.class);
        notifications = mock(NotificationService.class);
        offers = mock(OfferRepository.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        OfferService offerService = new OfferService(offers, listings, orders, users, notifications, tx);
        service = new OrderService(orders, listings, users, wallet, notifications, offerService, tx, 48);

        seller = user(SELLER_ID);
        buyer = user(BUYER_ID);
        admin = user(ADMIN_ID);
        admin.setRole(Role.ADMIN);
        user(STRANGER_ID);

        listing = new Listing();
        ReflectionTestUtils.setField(listing, "id", LISTING_ID);
        listing.setSeller(seller);
        listing.setTitle("Linen shade table lamp");
        listing.setPrice(new BigDecimal("18.00"));
        when(listings.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));
        when(orders.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", ORDER_ID);
            return o;
        });
    }

    private User user(long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername("user" + id);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static CheckoutForm form() {
        CheckoutForm f = new CheckoutForm();
        f.setShipName("Mikel");
        f.setShipStreet("Gedimino pr. 9");
        f.setShipCity("Vilnius");
        f.setShipPostalCode("LT-01103");
        return f;
    }

    /** An order of the listing in the given status, returned by the locking finder. */
    private Order stored(OrderStatus status) {
        listing.setStatus(ListingStatus.SOLD);
        Order o = new Order(listing, buyer, listing.getPrice(),
                new ShippingAddress("Mikel", "Gedimino pr. 9", "Vilnius", "LT-01103"), null);
        ReflectionTestUtils.setField(o, "id", ORDER_ID);
        if (status != OrderStatus.PAID && status != OrderStatus.CANCELLED) {
            o.ship("TRACK-1");
        }
        if (status == OrderStatus.DELIVERED || status == OrderStatus.COMPLETED) {
            o.confirmDelivery();
        }
        if (status == OrderStatus.COMPLETED) {
            o.complete();
        }
        if (status == OrderStatus.CANCELLED) {
            o.cancel();
        }
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(o));
        return o;
    }

    // ================================================================ placeOrder

    @Test
    void placeOrderPaysMarksSoldAndNotifiesTheSeller() {
        Order order = service.placeOrder(BUYER_ID, LISTING_ID, form());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPrice()).isEqualByComparingTo("18.00");
        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getSeller()).isSameAs(seller);
        assertThat(order.getShipping().getCity()).isEqualTo("Vilnius");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        verify(wallet).pay(buyer, order);
        verify(notifications).notify(eq(seller), eq(NotificationType.ITEM_SOLD), anyString(), eq("/orders/" + ORDER_ID));
    }

    @Test
    void ownListingCannotBeBought() {
        assertThatThrownBy(() -> service.placeOrder(SELLER_ID, LISTING_ID, form()))
                .isInstanceOf(BusinessRuleException.class);
        verify(wallet, never()).pay(any(), any());
        verify(orders, never()).save(any());
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void onlyActiveListingsCanBeBought() {
        for (ListingStatus status : new ListingStatus[] {ListingStatus.SOLD, ListingStatus.RESERVED}) {
            listing.setStatus(status);
            assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, form()))
                    .isInstanceOf(BusinessRuleException.class);
        }
        listing.setStatus(ListingStatus.HIDDEN);
        assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, form()))
                .isInstanceOf(NotFoundException.class);
        verify(wallet, never()).pay(any(), any());
    }

    @Test
    void bannedSellerCannotSell() {
        seller.setStatus(UserStatus.BANNED);
        assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, form()))
                .isInstanceOf(BusinessRuleException.class);
        verify(wallet, never()).pay(any(), any());
    }

    @Test
    void lowBalanceStopsTheOrderBeforeTheListingIsSold() {
        doThrow(new BusinessRuleException("Your wallet balance is too low.")).when(wallet).pay(any(), any());
        assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, form()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too low");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(notifications, never()).notify(any(), any(), anyString(), anyString());
    }

    @Test
    void unknownOfferIsRefused() {
        CheckoutForm f = form();
        f.setOfferId(77L);
        when(offers.findById(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, f))
                .isInstanceOf(BusinessRuleException.class);
        verify(wallet, never()).pay(any(), any());
    }

    // ================================================================ checkout with an accepted offer

    private static final long OFFER_ID = 7100L;

    /** An offer of the buyer on the listing, accepted (listing RESERVED) unless status says otherwise. */
    private Offer offer(User who, LocalDateTime madeAt, boolean accept) {
        Offer o = Offer.fromBuyer(listing, who, new BigDecimal("15.00"), madeAt);
        ReflectionTestUtils.setField(o, "id", OFFER_ID);
        if (accept) {
            o.accept(madeAt.plusMinutes(5));
            listing.setStatus(ListingStatus.RESERVED);
        }
        when(offers.findById(OFFER_ID)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void resolvePriceUsesTheAcceptedAmount() {
        offer(buyer, LocalDateTime.now().minusHours(1), true);
        assertThat(service.resolvePrice(listing, buyer, OFFER_ID)).isEqualByComparingTo("15.00");
        assertThat(service.resolvePrice(listing, buyer, null)).isEqualByComparingTo("18.00");
    }

    @Test
    void resolvePriceRefusesAnExpiredAcceptance() {
        offer(buyer, LocalDateTime.now().minusHours(30), true); // accepted 30 h ago, 24 h to check out
        assertThatThrownBy(() -> service.resolvePrice(listing, buyer, OFFER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("24 hours");
    }

    @Test
    void resolvePriceRefusesSomeoneElsesOffer() {
        offer(buyer, LocalDateTime.now().minusHours(1), true);
        User stranger = users.findById(STRANGER_ID).orElseThrow();
        assertThatThrownBy(() -> service.resolvePrice(listing, stranger, OFFER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resolvePriceRefusesAnOfferThatAlreadyBecameAnOrder() {
        offer(buyer, LocalDateTime.now().minusHours(1), true);
        when(orders.existsByOfferId(OFFER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.resolvePrice(listing, buyer, OFFER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resolvePriceRefusesAPendingOffer() {
        offer(buyer, LocalDateTime.now().minusHours(1), false);
        assertThatThrownBy(() -> service.resolvePrice(listing, buyer, OFFER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not been accepted");
    }

    @Test
    void theReservedBuyerChecksOutAtTheAgreedPrice() {
        offer(buyer, LocalDateTime.now().minusHours(1), true);
        CheckoutForm f = form();
        f.setOfferId(OFFER_ID);

        Order order = service.placeOrder(BUYER_ID, LISTING_ID, f);

        assertThat(order.getPrice()).isEqualByComparingTo("15.00");
        assertThat(order.getOfferId()).isEqualTo(OFFER_ID);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        verify(wallet).pay(buyer, order);
    }

    @Test
    void aReservedListingCannotBeBoughtWithoutTheOffer() {
        offer(buyer, LocalDateTime.now().minusHours(1), true);
        assertThatThrownBy(() -> service.placeOrder(BUYER_ID, LISTING_ID, form()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reserved");
        verify(wallet, never()).pay(any(), any());
    }

    @Test
    void buyNowDeclinesTheOtherOpenOffers() {
        User other = users.findById(STRANGER_ID).orElseThrow();
        Offer pending = Offer.fromBuyer(listing, other, new BigDecimal("12.00"), LocalDateTime.now());
        ReflectionTestUtils.setField(pending, "id", 7200L);
        when(offers.findOnListingForUpdate(eq(LISTING_ID), any(), eq(-1L))).thenReturn(List.of(pending));

        service.placeOrder(BUYER_ID, LISTING_ID, form());

        assertThat(pending.getStatus()).isEqualTo(OfferStatus.REJECTED);
        verify(notifications).notify(eq(other), eq(NotificationType.OFFER_ANSWERED), anyString(), eq("/offers/7200"));
    }

    // ================================================================ ship, deliver, complete

    @Test
    void onlyTheSellerShips() {
        Order o = stored(OrderStatus.PAID);
        assertThatThrownBy(() -> service.markShipped(BUYER_ID, ORDER_ID, "TRACK-1"))
                .isInstanceOf(BusinessRuleException.class);
        service.markShipped(SELLER_ID, ORDER_ID, "TRACK-1");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(notifications).notify(eq(buyer), eq(NotificationType.ORDER_STATUS), anyString(), anyString());
    }

    @Test
    void onlyTheBuyerConfirmsDelivery() {
        Order o = stored(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.confirmDelivery(SELLER_ID, ORDER_ID))
                .isInstanceOf(BusinessRuleException.class);
        service.confirmDelivery(BUYER_ID, ORDER_ID);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(notifications).notify(eq(seller), eq(NotificationType.ORDER_STATUS), anyString(), anyString());
    }

    @Test
    void completeReleasesThePaymentToTheSeller() {
        Order o = stored(OrderStatus.DELIVERED);
        service.complete(BUYER_ID, ORDER_ID);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(wallet).releaseToSeller(o);
        verify(notifications).notify(eq(seller), eq(NotificationType.ORDER_STATUS), anyString(), anyString());
    }

    @Test
    void completeIsBuyerOnlyAndNeedsDelivered() {
        stored(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> service.complete(SELLER_ID, ORDER_ID)).isInstanceOf(BusinessRuleException.class);
        stored(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.complete(BUYER_ID, ORDER_ID)).isInstanceOf(BusinessRuleException.class);
        verify(wallet, never()).releaseToSeller(any());
    }

    // ================================================================ cancel

    @Test
    void buyerCancelRefundsReopensAndNotifiesTheSeller() {
        Order o = stored(OrderStatus.PAID);
        service.cancel(BUYER_ID, ORDER_ID, "Found one closer to home");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(wallet).refund(o);
        verify(notifications).notify(eq(seller), eq(NotificationType.ORDER_STATUS),
                org.mockito.ArgumentMatchers.contains("Found one closer to home"), anyString());
    }

    @Test
    void sellerCancelNotifiesTheBuyer() {
        stored(OrderStatus.PAID);
        service.cancel(SELLER_ID, ORDER_ID, null);
        verify(notifications).notify(eq(buyer), eq(NotificationType.ORDER_STATUS), anyString(), anyString());
        verify(notifications, never()).notify(eq(seller), any(), anyString(), anyString());
    }

    @Test
    void adminMayCancelAndBothPartiesAreTold() {
        Order o = stored(OrderStatus.PAID);
        service.cancel(ADMIN_ID, ORDER_ID, "Reported as prohibited");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(wallet).refund(o);
        verify(notifications, times(2)).notify(any(), eq(NotificationType.ORDER_STATUS), anyString(), anyString());
    }

    @Test
    void strangersCannotCancel() {
        Order o = stored(OrderStatus.PAID);
        assertThatThrownBy(() -> service.cancel(STRANGER_ID, ORDER_ID, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(wallet, never()).refund(any());
    }

    @Test
    void onlyPaidOrdersCanBeCancelled() {
        stored(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.cancel(BUYER_ID, ORDER_ID, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        verify(wallet, never()).refund(any());
    }

    @Test
    void reasonLongerThan200IsRefused() {
        stored(OrderStatus.PAID);
        assertThatThrownBy(() -> service.cancel(BUYER_ID, ORDER_ID, "x".repeat(201)))
                .isInstanceOf(BusinessRuleException.class);
        verify(wallet, never()).refund(any());
    }

    // ================================================================ view

    @Test
    void strangersDoNotSeeTheOrderButAdminsDo() {
        Order o = stored(OrderStatus.PAID);
        when(orders.findDetailById(ORDER_ID)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.view(STRANGER_ID, ORDER_ID)).isInstanceOf(NotFoundException.class);
        assertThat(service.view(ADMIN_ID, ORDER_ID).isAdminView()).isTrue();
        assertThat(service.view(SELLER_ID, ORDER_ID).isCanShip()).isTrue();
        assertThat(service.view(BUYER_ID, ORDER_ID).isCanShip()).isFalse();
        assertThat(service.view(BUYER_ID, ORDER_ID).isCanCancel()).isTrue();
    }

    // ================================================================ scheduler

    @Test
    void autoCompleteOnlyCompletesOrdersPastTheThreshold() {
        Order old = deliveredOrder(101L, LocalDateTime.now().minusHours(60));
        Order recent = deliveredOrder(102L, LocalDateTime.now().minusHours(2)); // re-checked under the lock
        Order done = deliveredOrder(103L, LocalDateTime.now().minusHours(70));
        done.complete();
        when(orders.findDeliveredBefore(any(LocalDateTime.class))).thenReturn(List.of(101L, 102L, 103L));

        int count = service.autoCompleteDelivered();

        assertThat(count).isEqualTo(1);
        assertThat(old.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(recent.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(wallet).releaseToSeller(old);
        verify(wallet, never()).releaseToSeller(recent);
        verify(wallet, never()).releaseToSeller(done);
    }

    @Test
    void autoCompleteKeepsGoingAfterAFailure() {
        Order first = deliveredOrder(201L, LocalDateTime.now().minusHours(50));
        Order second = deliveredOrder(202L, LocalDateTime.now().minusHours(50));
        doThrow(new IllegalStateException("wallet missing")).when(wallet).releaseToSeller(first);
        when(orders.findDeliveredBefore(any(LocalDateTime.class))).thenReturn(List.of(201L, 202L));

        assertThat(service.autoCompleteDelivered()).isEqualTo(1);
        verify(wallet).releaseToSeller(second);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void autoCompleteAsksForOrdersOlderThan48Hours() {
        when(orders.findDeliveredBefore(any(LocalDateTime.class))).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusHours(48);
        assertThat(service.autoCompleteDelivered()).isZero();
        org.mockito.ArgumentCaptor<LocalDateTime> cutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orders).findDeliveredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusSeconds(5), before.plusSeconds(5));
    }

    private Order deliveredOrder(long id, LocalDateTime deliveredAt) {
        Listing l = new Listing();
        l.setSeller(seller);
        l.setTitle("Item " + id);
        l.setPrice(new BigDecimal("10.00"));
        Order o = new Order(l, buyer, l.getPrice(),
                new ShippingAddress("Mikel", "Gedimino pr. 9", "Vilnius", "LT-01103"), null);
        ReflectionTestUtils.setField(o, "id", id);
        o.ship("TRACK-" + id);
        o.confirmDelivery();
        ReflectionTestUtils.setField(o, "deliveredAt", deliveredAt);
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(o));
        return o;
    }
}
