package com.rehand.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.rehand.exception.BusinessRuleException;

/** The offer state machine without Spring or a database. */
class OfferTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 12, 14, 10, 0);

    private User seller;
    private User buyer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        seller = user(2L, "ausra_k");
        buyer = user(3L, "mikel_b");
        listing = new Listing();
        ReflectionTestUtils.setField(listing, "id", 1000L);
        listing.setSeller(seller);
        listing.setTitle("Quilted puffer jacket");
        listing.setPrice(new BigDecimal("38.00"));
    }

    private static User user(long id, String name) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername(name);
        return u;
    }

    private Offer pending() {
        return Offer.fromBuyer(listing, buyer, new BigDecimal("30.00"), T0);
    }

    @Test
    void aNewOfferIsPendingForFortyEightHours() {
        Offer o = pending();
        assertThat(o.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(o.getMadeBy()).isEqualTo(Party.BUYER);
        assertThat(o.responder()).isEqualTo(Party.SELLER);
        assertThat(o.getExpiresAt()).isEqualTo(T0.plusHours(48));
        assertThat(o.isExpired(T0.plusHours(47))).isFalse();
        assertThat(o.isExpired(T0.plusHours(48))).isTrue();
    }

    @Test
    void acceptGivesTheBuyerTwentyFourHoursToCheckOut() {
        Offer o = pending();
        o.accept(T0.plusHours(2));
        assertThat(o.isAccepted()).isTrue();
        assertThat(o.getExpiresAt()).isEqualTo(T0.plusHours(26));
    }

    @Test
    void anExpiredOfferCannotBeAcceptedOrCountered() {
        Offer o = pending();
        assertThatThrownBy(() -> o.accept(T0.plusHours(49))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> o.counter(new BigDecimal("35.00"), T0.plusHours(49)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(o.isPending()).isTrue();
    }

    @Test
    void counterCreatesAPendingOfferFromTheOtherParty() {
        Offer first = pending();
        Offer counter = first.counter(new BigDecimal("35.00"), T0.plusHours(1));

        assertThat(first.getStatus()).isEqualTo(OfferStatus.COUNTERED);
        assertThat(counter.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(counter.getMadeBy()).isEqualTo(Party.SELLER);
        assertThat(counter.responder()).isEqualTo(Party.BUYER);
        assertThat(counter.getBuyer()).isSameAs(buyer);
        assertThat(counter.getListing()).isSameAs(listing);
        assertThat(counter.getParent()).isSameAs(first);
        assertThat(counter.getExpiresAt()).isEqualTo(T0.plusHours(49));
        assertThat(counter.getMaker()).isSameAs(seller);

        Offer again = counter.counter(new BigDecimal("32.00"), T0.plusHours(2));
        assertThat(again.getMadeBy()).isEqualTo(Party.BUYER);
        assertThat(again.getParent()).isSameAs(counter);
    }

    @Test
    void counterNeedsADifferentPositiveAmount() {
        Offer o = pending();
        assertThatThrownBy(() -> o.counter(new BigDecimal("30.00"), T0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> o.counter(BigDecimal.ZERO, T0)).isInstanceOf(BusinessRuleException.class);
        assertThat(o.isPending()).isTrue();
    }

    @Test
    void onlyPendingOffersCanBeAnswered() {
        Offer rejected = pending();
        rejected.reject();
        assertThat(rejected.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThatThrownBy(rejected::reject).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> rejected.accept(T0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(rejected::withdraw).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> rejected.counter(new BigDecimal("31.00"), T0)).isInstanceOf(BusinessRuleException.class);

        Offer accepted = pending();
        accepted.accept(T0);
        assertThatThrownBy(accepted::reject).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(accepted::withdraw).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdrawClosesAPendingOffer() {
        Offer o = pending();
        o.withdraw();
        assertThat(o.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    void pendingAndAcceptedOffersCanExpireOthersCannot() {
        Offer p = pending();
        p.expire();
        assertThat(p.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThatThrownBy(p::expire).isInstanceOf(BusinessRuleException.class);

        Offer a = pending();
        a.accept(T0);
        a.expire();
        assertThat(a.getStatus()).isEqualTo(OfferStatus.EXPIRED);

        Offer c = pending();
        c.counter(new BigDecimal("35.00"), T0);
        assertThatThrownBy(c::expire).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void partiesAreRecognised() {
        Offer o = pending();
        assertThat(o.partyOf(3L)).isEqualTo(Party.BUYER);
        assertThat(o.partyOf(2L)).isEqualTo(Party.SELLER);
        assertThat(o.partyOf(9L)).isNull();
        assertThat(o.isParty(9L)).isFalse();
        assertThat(o.userOf(Party.SELLER)).isSameAs(seller);
    }

    @Test
    void listingReservationRules() {
        listing.reserve();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listing.canBePurchasedBy(buyer)).isFalse();
        assertThat(listing.canBePurchasedBy(buyer, true)).isTrue();
        assertThat(listing.canBePurchasedBy(seller, true)).isFalse();
        assertThatThrownBy(listing::reserve).isInstanceOf(BusinessRuleException.class);
        listing.releaseReservation();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThatThrownBy(listing::releaseReservation).isInstanceOf(BusinessRuleException.class);
    }
}
