package com.rehand.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.rehand.domain.ShippingAddress;

/**
 * Backing object of the checkout form: where the parcel goes. {@code offerId} is a hidden field:
 * empty for buy now, the id of the accepted offer when the buyer checks out at the agreed price.
 */
public class CheckoutForm {

    /** Letters, digits, spaces and hyphens, starting and ending with a letter or digit (LT-44248, 48001, SW1A 1AA). */
    public static final String POSTAL_CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]$";

    private Long offerId;

    @NotBlank(message = "Enter the name on the parcel.")
    @Size(max = 100, message = "The name can have at most 100 characters.")
    private String shipName;

    @NotBlank(message = "Enter the street and house number.")
    @Size(min = 3, max = 200, message = "The street must have 3 to 200 characters.")
    private String shipStreet;

    @NotBlank(message = "Enter the city.")
    @Size(max = 80, message = "The city can have at most 80 characters.")
    private String shipCity;

    @NotBlank(message = "Enter the postal code.")
    @Pattern(regexp = POSTAL_CODE_PATTERN, message = "Use 3 to 12 letters, digits, spaces or hyphens, for example LT-44248.")
    private String shipPostalCode;

    public CheckoutForm() {
    }

    /** The address as the embedded value stored on the order. Call only after validation. */
    public ShippingAddress toAddress() {
        return new ShippingAddress(shipName, shipStreet, shipCity, shipPostalCode.toUpperCase(Locale.ROOT));
    }

    public Long getOfferId() {
        return offerId;
    }

    public void setOfferId(Long offerId) {
        this.offerId = offerId;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = FormText.trimToNull(shipName);
    }

    public String getShipStreet() {
        return shipStreet;
    }

    public void setShipStreet(String shipStreet) {
        this.shipStreet = FormText.trimToNull(shipStreet);
    }

    public String getShipCity() {
        return shipCity;
    }

    public void setShipCity(String shipCity) {
        this.shipCity = FormText.trimToNull(shipCity);
    }

    public String getShipPostalCode() {
        return shipPostalCode;
    }

    public void setShipPostalCode(String shipPostalCode) {
        this.shipPostalCode = FormText.trimToNull(shipPostalCode);
    }
}
