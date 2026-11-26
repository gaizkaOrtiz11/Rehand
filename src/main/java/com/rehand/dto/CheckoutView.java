package com.rehand.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.rehand.domain.Listing;
import com.rehand.domain.User;

/**
 * Everything the checkout page shows, loaded in one transaction: the listing (seller and photos
 * initialised), the buyer, the buyer's wallet balance and how much is missing to pay the price.
 */
public class CheckoutView {

    private final Listing listing;
    private final User buyer;
    private final BigDecimal price;
    private final BigDecimal balance;
    private final BigDecimal minTopUp;
    private final Long offerId;

    /** @param minTopUp smallest amount the top-up form accepts (the link never asks for less) */
    public CheckoutView(Listing listing, User buyer, BigDecimal price, BigDecimal balance, BigDecimal minTopUp) {
        this(listing, buyer, price, balance, minTopUp, null);
    }

    /** @param offerId the accepted offer being checked out (its amount is {@code price}), or null for buy now */
    public CheckoutView(Listing listing, User buyer, BigDecimal price, BigDecimal balance, BigDecimal minTopUp,
                        Long offerId) {
        this.offerId = offerId;
        this.listing = listing;
        this.buyer = buyer;
        this.price = price;
        this.balance = balance == null ? BigDecimal.ZERO.setScale(2) : balance;
        this.minTopUp = minTopUp == null ? BigDecimal.ZERO : minTopUp;
    }

    public Listing getListing() {
        return listing;
    }

    public User getBuyer() {
        return buyer;
    }

    /** The amount the buyer pays (the listing price, or the amount of an accepted offer). */
    public BigDecimal getPrice() {
        return price;
    }

    /** The accepted offer being checked out, or null. */
    public Long getOfferId() {
        return offerId;
    }

    /** True when the buyer pays an agreed price below the asking price. */
    public boolean isFromOffer() {
        return offerId != null;
    }

    /** The listing's asking price (shown crossed out next to an agreed price). */
    public BigDecimal getAskingPrice() {
        return listing.getPrice();
    }

    /** Asking price minus agreed price, never negative. */
    public BigDecimal getSaving() {
        BigDecimal saving = listing.getPrice().subtract(price);
        return (saving.signum() > 0 ? saving : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Where the top-up page sends the buyer back: this checkout, with the offer if there is one. */
    public String getReturnPath() {
        return "/checkout/" + listing.getId() + (offerId == null ? "" : "?offer=" + offerId);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isBalanceEnough() {
        return balance.compareTo(price) >= 0;
    }

    /** Price minus balance, never negative, two decimals (used for the top-up link). */
    public BigDecimal getMissing() {
        BigDecimal missing = price.subtract(balance);
        return (missing.signum() > 0 ? missing : BigDecimal.ZERO).setScale(2, RoundingMode.UP);
    }

    /** Amount suggested on the top-up link: what is missing, but at least the top-up minimum. */
    public BigDecimal getTopUpAmount() {
        BigDecimal missing = getMissing();
        return missing.compareTo(minTopUp) < 0 ? minTopUp.setScale(2, RoundingMode.UP) : missing;
    }

    /** Balance after paying, never negative. */
    public BigDecimal getBalanceAfter() {
        BigDecimal after = balance.subtract(price);
        return (after.signum() > 0 ? after : BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
    }

    public String getSellerInitials() {
        return Initials.of(listing.getSeller().getUsername());
    }
}
