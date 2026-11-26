package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.rehand.domain.Party;
import com.rehand.domain.User;
import com.rehand.domain.UserStatus;
import com.rehand.dto.NegotiationView;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.OfferRepository;
import com.rehand.repository.OrderRepository;
import com.rehand.repository.UserRepository;

/** Plain JUnit and Mockito: the offer rules of {@link OfferService} without Spring or a database. */
class OfferServiceTest {

    private static final long SELLER_ID = 2L;
    private static final long BUYER_ID = 3L;
    private static final long OTHER_BUYER_ID = 4L;
    private static final long STRANGER_ID = 9L;
    private static final long LISTING_ID = 1000L;

    private OfferRepository offers;
    private ListingRepository listings;
    private OrderRepository orders;
    private UserRepository users;
    private NotificationService notifications;
    private OfferService service;

    private User seller;
    private User buyer;
    private User otherBuyer;
    private Listing listing;
    private long nextId = 7000L;

    @BeforeEach
    void setUp() {
        offers = mock(OfferRepository.class);
        listings = mock(ListingRepository.class);
        orders = mock(OrderRepository.class);
        users = mock(UserRepository.class);
        notifications = mock(NotificationService.class);
        service = new OfferService(offers, listings, orders, users, notifications, mock(PlatformTransactionManager.class));

        seller = user(SELLER_ID);
        buyer = user(BUYER_ID);
        otherBuyer = user(OTHER_BUYER_ID);
        user(STRANGER_ID);

        listing = new Listing();
        ReflectionTestUtils.setField(listing, "id", LISTING_ID);
        listing.setSeller(seller);
        listing.setTitle("Quilted puffer jacket");
        listing.setPrice(new BigDecimal("38.00"));
        when(listings.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));
        when(offers.findByBuyerOnListing(eq(LISTING_ID), any(), any())).thenReturn(List.of());
        when(offers.findOnListingForUpdate(eq(LISTING_ID), any(), any())).thenReturn(List.of());
        when(offers.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", nextId++);
            }
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

    /** A stored offer, returned by the locking finders. */
    private Offer stored(Offer o) {
        ReflectionTestUtils.setField(o, "id", nextId++);
        when(offers.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(offers.findListingIdById(o.getId())).thenReturn(Optional.of(LISTING_ID));
        return o;
    }

    private Offer pendingFrom(User who, String amount, LocalDateTime madeAt) {
        return stored(Offer.fromBuyer(listing, who, new BigDecimal(amount), madeAt));
    }

    private Offer pendingFrom(User who, String amount) {
        return pendingFrom(who, amount, LocalDateTime.now().minusHours(1));
    }

    // ================================================================ make an offer

    @Test
    void makeOfferStoresAPendingOfferAndNotifiesTheSeller() {
        Offer o = service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00"));

        assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(o.getMadeBy()).isEqualTo(Party.BUYER);
        assertThat(o.getBuyer()).isSameAs(buyer);
        assertThat(o.getAmount()).isEqualByComparingTo("30.00");
        verify(notifications).notify(eq(seller), eq(NotificationType.OFFER_RECEIVED), anyString(), eq("/offers/" + o.getId()));
    }

    @Test
    void noOfferOnYourOwnListing() {
        assertThatThrownBy(() -> service.makeOffer(SELLER_ID, LISTING_ID, new BigDecimal("30.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("own listing");
        verify(offers, never()).save(any());
    }

    @Test
    void theOfferMustBeAboveZeroBelowThePriceWithTwoDecimals() {
        for (String bad : new String[] {"38.00", "40", "0", "-1", "12.345"}) {
            assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal(bad)))
                    .as(bad).isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("37.99")).getAmount()).isEqualByComparingTo("37.99");
    }

    @Test
    void onlyActiveListingsOfActiveSellersTakeOffers() {
        listing.setStatus(ListingStatus.RESERVED);
        assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00")))
                .isInstanceOf(BusinessRuleException.class);
        listing.setStatus(ListingStatus.HIDDEN);
        assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00")))
                .isInstanceOf(NotFoundException.class);
        listing.setStatus(ListingStatus.ACTIVE);
        seller.setStatus(UserStatus.BANNED);
        assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void oneOpenNegotiationPerBuyerAndListing() {
        Offer open = pendingFrom(buyer, "25.00");
        when(offers.findByBuyerOnListing(eq(LISTING_ID), eq(BUYER_ID), any())).thenReturn(List.of(open));
        assertThatThrownBy(() -> service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already have an open offer");
        verify(offers, never()).save(any());
    }

    @Test
    void anExpiredOrUsedOfferDoesNotBlockANewOne() {
        Offer stale = pendingFrom(buyer, "25.00", LocalDateTime.now().minusHours(50));
        Offer used = pendingFrom(buyer, "26.00", LocalDateTime.now().minusHours(2));
        used.accept(LocalDateTime.now().minusHours(1));
        when(orders.existsByOfferId(used.getId())).thenReturn(true);
        when(offers.findByBuyerOnListing(eq(LISTING_ID), eq(BUYER_ID), any())).thenReturn(List.of(used, stale));

        assertThat(service.makeOffer(BUYER_ID, LISTING_ID, new BigDecimal("30.00")).isPending()).isTrue();
    }

    // ================================================================ accept

    @Test
    void acceptReservesTheListingAndDeclinesTheOtherOffers() {
        Offer mine = pendingFrom(buyer, "30.00");
        Offer other = pendingFrom(otherBuyer, "28.00");
        when(offers.findOnListingForUpdate(eq(LISTING_ID), any(), eq(mine.getId()))).thenReturn(List.of(other));

        service.accept(SELLER_ID, mine.getId());

        assertThat(mine.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(mine.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(other.getStatus()).isEqualTo(OfferStatus.REJECTED);
        verify(notifications).notify(eq(buyer), eq(NotificationType.OFFER_ANSWERED), anyString(), eq("/offers/" + mine.getId()));
        verify(notifications).notify(eq(otherBuyer), eq(NotificationType.OFFER_ANSWERED), anyString(), eq("/offers/" + other.getId()));
    }

    @Test
    void onlyTheResponderMayAccept() {
        Offer o = pendingFrom(buyer, "30.00");
        assertThatThrownBy(() -> service.accept(BUYER_ID, o.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not your turn");
        assertThatThrownBy(() -> service.accept(STRANGER_ID, o.getId())).isInstanceOf(NotFoundException.class);
        assertThat(o.isPending()).isTrue();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void acceptNeedsAnActiveListing() {
        Offer o = pendingFrom(buyer, "30.00");
        listing.setStatus(ListingStatus.SOLD);
        assertThatThrownBy(() -> service.accept(SELLER_ID, o.getId())).isInstanceOf(BusinessRuleException.class);
        assertThat(o.isPending()).isTrue();
    }

    // ================================================================ counter and the chain

    @Test
    void counterChainSwitchesTheResponder() {
        Offer first = pendingFrom(buyer, "30.00");

        Offer sellerCounter = service.counter(SELLER_ID, first.getId(), new BigDecimal("35.00"));
        when(offers.findByIdForUpdate(sellerCounter.getId())).thenReturn(Optional.of(sellerCounter));
        when(offers.findListingIdById(sellerCounter.getId())).thenReturn(Optional.of(LISTING_ID));

        assertThat(first.getStatus()).isEqualTo(OfferStatus.COUNTERED);
        assertThat(sellerCounter.getMadeBy()).isEqualTo(Party.SELLER);
        assertThat(sellerCounter.responder()).isEqualTo(Party.BUYER);
        assertThat(sellerCounter.getParent()).isSameAs(first);
        verify(notifications).notify(eq(buyer), eq(NotificationType.OFFER_RECEIVED), anyString(),
                eq("/offers/" + sellerCounter.getId()));

        // now the seller must wait and the buyer answers
        assertThatThrownBy(() -> service.accept(SELLER_ID, sellerCounter.getId()))
                .isInstanceOf(BusinessRuleException.class);
        service.accept(BUYER_ID, sellerCounter.getId());
        assertThat(sellerCounter.isAccepted()).isTrue();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        verify(notifications).notify(eq(seller), eq(NotificationType.OFFER_ANSWERED), anyString(),
                eq("/offers/" + sellerCounter.getId()));
    }

    @Test
    void counterAmountMustBeBelowThePriceAndDifferent() {
        Offer o = pendingFrom(buyer, "30.00");
        assertThatThrownBy(() -> service.counter(SELLER_ID, o.getId(), new BigDecimal("38.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.counter(SELLER_ID, o.getId(), new BigDecimal("30.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(o.isPending()).isTrue();
    }

    @Test
    void theNegotiationPageShowsTheWholeChainToThePartiesOnly() {
        Offer first = pendingFrom(buyer, "30.00");
        Offer second = first.counter(new BigDecimal("35.00"), LocalDateTime.now().minusMinutes(30));
        ReflectionTestUtils.setField(second, "id", nextId++);
        Offer third = second.counter(new BigDecimal("33.00"), LocalDateTime.now().minusMinutes(10));
        ReflectionTestUtils.setField(third, "id", nextId++);
        when(offers.findDetailById(second.getId())).thenReturn(Optional.of(second));
        when(offers.findAllOfBuyerOnListing(LISTING_ID, BUYER_ID)).thenReturn(new ArrayList<>(List.of(first, second, third)));

        NegotiationView view = service.view(SELLER_ID, second.getId());
        List<Long> ids = new ArrayList<>();
        for (NegotiationView.Entry e : view.getEntries()) {
            ids.add(e.getOffer().getId());
        }
        assertThat(ids).containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(view.getLatest()).isSameAs(third);
        assertThat(view.isCanRespond()).isTrue(); // the buyer made the latest counter, the seller answers
        assertThat(view.isCanWithdraw()).isFalse();

        assertThatThrownBy(() -> service.view(STRANGER_ID, second.getId())).isInstanceOf(NotFoundException.class);
    }

    // ================================================================ reject and withdraw

    @Test
    void rejectClosesAndTellsTheMaker() {
        Offer o = pendingFrom(buyer, "30.00");
        service.reject(SELLER_ID, o.getId());
        assertThat(o.getStatus()).isEqualTo(OfferStatus.REJECTED);
        verify(notifications).notify(eq(buyer), eq(NotificationType.OFFER_ANSWERED), anyString(), anyString());
    }

    @Test
    void onlyTheMakerMayWithdraw() {
        Offer o = pendingFrom(buyer, "30.00");
        assertThatThrownBy(() -> service.withdraw(SELLER_ID, o.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only the member who made");
        assertThatThrownBy(() -> service.withdraw(STRANGER_ID, o.getId())).isInstanceOf(NotFoundException.class);
        service.withdraw(BUYER_ID, o.getId());
        assertThat(o.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
        verify(notifications).notify(eq(seller), eq(NotificationType.OFFER_ANSWERED), anyString(), anyString());
        assertThatThrownBy(() -> service.withdraw(BUYER_ID, o.getId())).isInstanceOf(BusinessRuleException.class);
    }

    // ================================================================ scheduler

    @Test
    void schedulerExpiresStalePendingOffers() {
        Offer stale = pendingFrom(buyer, "30.00", LocalDateTime.now().minusHours(49));
        Offer fresh = pendingFrom(otherBuyer, "29.00", LocalDateTime.now().minusHours(2)); // re-checked under lock
        when(offers.findExpiredIds(any(), any(LocalDateTime.class))).thenReturn(List.of(stale.getId(), fresh.getId()));

        assertThat(service.expireStaleOffers()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(fresh.getStatus()).isEqualTo(OfferStatus.PENDING);
        verify(notifications).notify(eq(buyer), eq(NotificationType.OFFER_ANSWERED), anyString(), anyString());
    }

    @Test
    void schedulerExpiresAnUnusedAcceptanceAndReopensTheListing() {
        Offer accepted = pendingFrom(buyer, "30.00", LocalDateTime.now().minusHours(30));
        accepted.accept(LocalDateTime.now().minusHours(25)); // 24 h to check out: over
        listing.setStatus(ListingStatus.RESERVED);
        when(offers.findExpiredIds(any(), any(LocalDateTime.class))).thenReturn(List.of(accepted.getId()));

        assertThat(service.expireStaleOffers()).isEqualTo(1);
        assertThat(accepted.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(notifications).notify(eq(buyer), eq(NotificationType.OFFER_ANSWERED), anyString(), anyString());
        verify(notifications).notify(eq(seller), eq(NotificationType.OFFER_ANSWERED), anyString(), anyString());
    }

    @Test
    void schedulerLeavesACheckedOutAcceptanceAlone() {
        Offer accepted = pendingFrom(buyer, "30.00", LocalDateTime.now().minusHours(30));
        accepted.accept(LocalDateTime.now().minusHours(25));
        listing.setStatus(ListingStatus.SOLD);
        when(orders.existsByOfferId(accepted.getId())).thenReturn(true);
        when(offers.findExpiredIds(any(), any(LocalDateTime.class))).thenReturn(List.of(accepted.getId()));

        assertThat(service.expireStaleOffers()).isZero();
        assertThat(accepted.isAccepted()).isTrue();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void closeOpenOffersDeclinesPendingOnes() {
        Offer a = pendingFrom(buyer, "30.00");
        Offer b = pendingFrom(otherBuyer, "31.00");
        when(offers.findOnListingForUpdate(eq(LISTING_ID), any(), eq(-1L))).thenReturn(List.of(a, b));

        assertThat(service.closeOpenOffers(listing, null, "the item was sold.")).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(b.getStatus()).isEqualTo(OfferStatus.REJECTED);
    }
}
