package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rehand.domain.ItemCondition;

/** Bean Validation rules of {@link ListingForm}, checked with a plain Validator (no Spring context). */
class ListingFormTest {

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

    private static ListingForm valid() {
        ListingForm f = new ListingForm();
        f.setTitle("Green jacket");
        f.setDescription("A warm green jacket in good condition.");
        f.setPrice(new BigDecimal("30.00"));
        f.setCondition(ItemCondition.GOOD);
        f.setCategoryId(11L);
        f.setCity("Kaunas");
        return f;
    }

    private static Set<String> invalid(ListingForm f) {
        return validator.validate(f).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void aCompleteFormIsValid() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void optionalFieldsMayBeMissing() {
        ListingForm f = valid();
        f.setBrand(null);
        f.setSize(null);
        f.setColor(null);
        assertThat(validator.validate(f)).isEmpty();
    }

    @Test
    void titleMustHaveThreeToOneHundredTwentyCharacters() {
        ListingForm f = valid();
        f.setTitle("ab");
        assertThat(invalid(f)).containsExactly("title");
        f.setTitle("abc");
        assertThat(validator.validate(f)).isEmpty();
        f.setTitle("a".repeat(120));
        assertThat(validator.validate(f)).isEmpty();
        f.setTitle("a".repeat(121));
        assertThat(invalid(f)).containsExactly("title");
        f.setTitle("   ");
        assertThat(invalid(f)).contains("title");
        f.setTitle(null);
        assertThat(invalid(f)).contains("title");
    }

    @Test
    void descriptionMustHaveTenToFourThousandCharacters() {
        ListingForm f = valid();
        f.setDescription("too short");
        assertThat(invalid(f)).containsExactly("description");
        f.setDescription("ten chars!");
        assertThat(validator.validate(f)).isEmpty();
        f.setDescription("a".repeat(4000));
        assertThat(validator.validate(f)).isEmpty();
        f.setDescription("a".repeat(4001));
        assertThat(invalid(f)).containsExactly("description");
        f.setDescription(null);
        assertThat(invalid(f)).contains("description");
    }

    @Test
    void priceMustBeBetweenOneCentAndTheLimitWithTwoDecimals() {
        ListingForm f = valid();
        f.setPrice(null);
        assertThat(invalid(f)).containsExactly("price");
        f.setPrice(BigDecimal.ZERO);
        assertThat(invalid(f)).containsExactly("price");
        f.setPrice(new BigDecimal("-5"));
        assertThat(invalid(f)).containsExactly("price");
        f.setPrice(new BigDecimal("0.01"));
        assertThat(validator.validate(f)).isEmpty();
        f.setPrice(new BigDecimal("99999.99"));
        assertThat(validator.validate(f)).isEmpty();
        f.setPrice(new BigDecimal("100000.00"));
        assertThat(invalid(f)).containsExactly("price");
        f.setPrice(new BigDecimal("12.345"));
        assertThat(invalid(f)).containsExactly("price");
    }

    @Test
    void conditionAndCategoryAreRequired() {
        ListingForm f = valid();
        f.setCondition(null);
        f.setCategoryId(null);
        assertThat(invalid(f)).containsExactlyInAnyOrder("condition", "categoryId");
    }

    @Test
    void cityIsRequired() {
        ListingForm f = valid();
        f.setCity("  ");
        assertThat(invalid(f)).containsExactly("city");
        f.setCity("x".repeat(81));
        assertThat(invalid(f)).containsExactly("city");
    }

    @Test
    void brandSizeAndColorHaveMaximumLengths() {
        ListingForm f = valid();
        f.setBrand("b".repeat(61));
        f.setSize("s".repeat(21));
        f.setColor("c".repeat(31));
        assertThat(invalid(f)).containsExactlyInAnyOrder("brand", "size", "color");
        f.setBrand("b".repeat(60));
        f.setSize("s".repeat(20));
        f.setColor("c".repeat(30));
        assertThat(validator.validate(f)).isEmpty();
    }

    @Test
    void violationsCarryReadableMessages() {
        ListingForm f = valid();
        f.setTitle("ab");
        ConstraintViolation<ListingForm> v = validator.validate(f).iterator().next();
        assertThat(v.getMessage()).isEqualTo("The title must be 3 to 120 characters long.");
    }
}
