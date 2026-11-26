package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rehand.domain.ShippingAddress;

/** Bean Validation of the checkout, ship and cancel forms, without a Spring context. */
class CheckoutFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static CheckoutForm valid() {
        CheckoutForm f = new CheckoutForm();
        f.setShipName("Mikel Bengoetxea");
        f.setShipStreet("Gedimino pr. 9-4");
        f.setShipCity("Vilnius");
        f.setShipPostalCode("LT-01103");
        return f;
    }

    private static Set<String> invalidFields(Object form) {
        return validator.validate(form).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validAddressPasses() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    void blankFieldsAreTrimmedAndRejected() {
        CheckoutForm f = new CheckoutForm();
        f.setShipName("   ");
        f.setShipStreet("");
        assertThat(f.getShipName()).isNull();
        assertThat(invalidFields(f)).containsExactlyInAnyOrder("shipName", "shipStreet", "shipCity", "shipPostalCode");
    }

    @Test
    void postalCodeFormats() {
        for (String ok : new String[] {"LT-44248", "48001", "SW1A 1AA", "123"}) {
            CheckoutForm f = valid();
            f.setShipPostalCode(ok);
            assertThat(invalidFields(f)).as(ok).isEmpty();
        }
        for (String bad : new String[] {"12", "-1234", "1234-", "LT_44248", "1234567890123"}) {
            CheckoutForm f = valid();
            f.setShipPostalCode(bad);
            assertThat(invalidFields(f)).as(bad).containsExactly("shipPostalCode");
        }
    }

    @Test
    void tooLongValuesAreRejected() {
        CheckoutForm f = valid();
        f.setShipName("a".repeat(101));
        f.setShipCity("b".repeat(81));
        f.setShipStreet("c".repeat(201));
        assertThat(invalidFields(f)).containsExactlyInAnyOrder("shipName", "shipCity", "shipStreet");
    }

    @Test
    void addressIsCopiedWithUpperCasePostalCode() {
        CheckoutForm f = valid();
        f.setShipPostalCode("lt-01103");
        ShippingAddress a = f.toAddress();
        assertThat(a.getName()).isEqualTo("Mikel Bengoetxea");
        assertThat(a.getPostalCode()).isEqualTo("LT-01103");
    }

    @Test
    void shipAndCancelForms() {
        ShipForm ship = new ShipForm();
        assertThat(invalidFields(ship)).containsExactly("trackingCode");
        ship.setTrackingCode("abc");
        assertThat(invalidFields(ship)).containsExactly("trackingCode");
        ship.setTrackingCode(" LP123456789LT ");
        assertThat(ship.getTrackingCode()).isEqualTo("LP123456789LT");
        assertThat(invalidFields(ship)).isEmpty();

        CancelForm cancel = new CancelForm();
        assertThat(invalidFields(cancel)).isEmpty();
        cancel.setReason("x".repeat(201));
        assertThat(invalidFields(cancel)).containsExactly("reason");
        cancel.setReason("   ");
        assertThat(cancel.getReason()).isNull();
    }
}
