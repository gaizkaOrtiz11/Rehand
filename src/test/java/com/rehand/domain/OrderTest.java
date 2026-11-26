package com.rehand.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.rehand.exception.BusinessRuleException;

/** The order state machine and the purchase rules on {@link Listing} (no database). */
class OrderTest {

    private User seller;
    private User buyer;
    private User stranger;
    private Listing listing;

    static User user(long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername("user" + id);
        return u;
    }

    @BeforeEach
    void setUp() {
        seller = user(2L);
        buyer = user(3L);
        stranger = user(9L);
        listing = new Listing();
        listing.setSeller(seller);
        listing.setTitle("Linen lamp");
        listing.setPrice(new BigDecimal("18.00"));
    }

    private Order newOrder() {
        return new Order(listing, buyer, listing.getPrice(),
                new ShippingAddress("Mikel", "Gedimino pr. 9", "Vilnius", "LT-01103"), null);
    }

    // ------------------------------------------------------------ happy path

    @Test
    void newOrderIsPaidAndKnowsItsParties() {
        Order o = newOrder();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getSeller()).isSameAs(seller);
        assertThat(o.isBuyer(3L)).isTrue();
        assertThat(o.isSeller(2L)).isTrue();
        assertThat(o.isBuyer(2L)).isFalse();
        assertThat(o.isSeller(3L)).isFalse();
        assertThat(o.isParty(9L)).isFalse();
        assertThat(o.isBuyer(null)).isFalse();
        assertThat(o.counterpartOf(3L)).isSameAs(seller);
        assertThat(o.counterpartOf(2L)).isSameAs(buyer);
    }

    @Test
    void fullFlowSetsEveryTimestamp() {
        Order o = newOrder();
        o.ship("  LP123456789LT ");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(o.getTrackingCode()).isEqualTo("LP123456789LT");
        assertThat(o.getShippedAt()).isNotNull();

        o.confirmDelivery();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.getDeliveredAt()).isNotNull();

        o.complete();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(o.getCompletedAt()).isNotNull();
        assertThat(o.getCancelledAt()).isNull();
        assertThat(o.isOpen()).isFalse();
    }

    @Test
    void paidOrderCanBeCancelled() {
        Order o = newOrder();
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.getCancelledAt()).isNotNull();
    }

    // ------------------------------------------------------------ illegal transitions

    @Test
    void cannotSkipSteps() {
        Order o = newOrder();
        assertThatThrownBy(o::confirmDelivery).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(o::complete).isInstanceOf(BusinessRuleException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);

        o.ship("TRACK-1");
        assertThatThrownBy(o::complete).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> o.ship("TRACK-2")).isInstanceOf(BusinessRuleException.class);
        assertThat(o.getTrackingCode()).isEqualTo("TRACK-1");
    }

    @Test
    void cannotCancelOnceShipped() {
        Order o = newOrder();
        o.ship("TRACK-1");
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
        o.confirmDelivery();
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
        o.complete();
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void cancelledOrderIsFinal() {
        Order o = newOrder();
        o.cancel();
        assertThatThrownBy(() -> o.ship("TRACK-1")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(o::confirmDelivery).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void trackingCodeMustHaveFourToSixtyCharacters() {
        Order o = newOrder();
        assertThatThrownBy(() -> o.ship(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> o.ship(" ab ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> o.ship("x".repeat(61))).isInstanceOf(BusinessRuleException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
        o.ship("x".repeat(60));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void onlyPartiesReviewAndOnlyWhenCompleted() {
        Order o = newOrder();
        assertThat(o.canBeReviewedBy(buyer)).isFalse();
        o.ship("TRACK-1");
        o.confirmDelivery();
        assertThat(o.canBeReviewedBy(seller)).isFalse();
        o.complete();
        assertThat(o.canBeReviewedBy(buyer)).isTrue();
        assertThat(o.canBeReviewedBy(seller)).isTrue();
        assertThat(o.canBeReviewedBy(stranger)).isFalse();
        assertThat(o.canBeReviewedBy(null)).isFalse();
    }

    // ------------------------------------------------------------ listing rules used by checkout

    @Test
    void onlyActiveListingsOfOtherMembersCanBeBought() {
        assertThat(listing.canBePurchasedBy(buyer)).isTrue();
        assertThat(listing.canBePurchasedBy(seller)).isFalse();
        assertThat(listing.canBePurchasedBy(null)).isFalse();

        listing.hide();
        assertThat(listing.canBePurchasedBy(buyer)).isFalse();
        listing.unhide();

        seller.setStatus(UserStatus.BANNED);
        assertThat(listing.canBePurchasedBy(buyer)).isFalse();
    }

    @Test
    void markSoldAndReopen() {
        listing.markSold();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(listing.canBePurchasedBy(buyer)).isFalse();
        assertThatThrownBy(listing::markSold).isInstanceOf(BusinessRuleException.class);

        listing.reopen();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThatThrownBy(listing::reopen).isInstanceOf(BusinessRuleException.class);
    }
}
