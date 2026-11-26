package com.rehand.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Delivery address copied onto each order (a deliberate snapshot: later profile changes do not
 * change where an old order went). Embedded in {@link Order}; the columns are ship_name, ship_street,
 * ship_city and ship_postal_code (see the @AttributeOverrides there).
 */
@Embeddable
public class ShippingAddress {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "street", nullable = false, length = 200)
    private String street;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 12)
    private String postalCode;

    protected ShippingAddress() {
        // required by JPA
    }

    public ShippingAddress(String name, String street, String city, String postalCode) {
        this.name = name;
        this.street = street;
        this.city = city;
        this.postalCode = postalCode;
    }

    public String getName() {
        return name;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShippingAddress other)) {
            return false;
        }
        return Objects.equals(name, other.name) && Objects.equals(street, other.street)
                && Objects.equals(city, other.city) && Objects.equals(postalCode, other.postalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, street, city, postalCode);
    }
}
