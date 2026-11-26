package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Bean Validation of the wallet forms, without a Spring context. */
class WalletFormsValidationTest {

    private static final DateTimeFormatter MM_YY = DateTimeFormatter.ofPattern("MM/yy");

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

    private static Set<String> invalidFields(Object form) {
        return validator.validate(form).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static TopUpForm validTopUp() {
        TopUpForm form = new TopUpForm();
        form.setPreset("25");
        form.setCardholderName("Gaizka B");
        form.setCardNumber("4242 4242 4242 4242");
        form.setExpiry(YearMonth.now().plusYears(2).format(MM_YY));
        form.setCvc("123");
        return form;
    }

    private static WithdrawForm validWithdraw() {
        WithdrawForm form = new WithdrawForm();
        form.setAmount(new BigDecimal("20.00"));
        form.setAccountHolder("Ausra K");
        form.setIban("LT12 1000 0111 0100 1000");
        return form;
    }

    // ---------------------------------------------------------------- top-up

    @Test
    void validTopUpHasNoErrors() {
        assertThat(invalidFields(validTopUp())).isEmpty();
        assertThat(validTopUp().getAmount()).isEqualByComparingTo("25");
    }

    @Test
    void typedAmountWinsOverPreset() {
        TopUpForm form = validTopUp();
        form.setCustomAmount(new BigDecimal("37.50"));
        assertThat(form.getAmount()).isEqualByComparingTo("37.50");
        assertThat(form.isCustom()).isTrue();
        assertThat(invalidFields(form)).isEmpty();
    }

    @Test
    void amountOutsideLimitsIsRejected() {
        TopUpForm low = validTopUp();
        low.setCustomAmount(new BigDecimal("4.99"));
        assertThat(invalidFields(low)).containsExactly("amount");

        TopUpForm high = validTopUp();
        high.setPreset("5000");
        assertThat(invalidFields(high)).containsExactly("amount");

        TopUpForm none = validTopUp();
        none.setPreset(TopUpForm.CUSTOM);
        assertThat(invalidFields(none)).containsExactly("amount");

        TopUpForm decimals = validTopUp();
        decimals.setCustomAmount(new BigDecimal("10.555"));
        assertThat(invalidFields(decimals)).contains("customAmount", "amount");
    }

    @Test
    void cardFieldsAreChecked() {
        TopUpForm form = validTopUp();
        form.setCardNumber("4242 4242 4242 4241");
        form.setExpiry(YearMonth.now().minusMonths(1).format(MM_YY));
        form.setCvc("12a");
        form.setCardholderName("  ");
        assertThat(invalidFields(form)).containsExactlyInAnyOrder("cardNumber", "expiry", "cvc", "cardholderName");
    }

    @Test
    void clearSensitiveForgetsCardNumberAndCvc() {
        TopUpForm form = validTopUp();
        form.clearSensitive();
        assertThat(form.getCardNumber()).isNull();
        assertThat(form.getCvc()).isNull();
        assertThat(form.toString()).doesNotContain("4242");
    }

    @Test
    void prefillMatchesPresetOrUsesCustomAndClamps() {
        assertThat(TopUpForm.prefilled(new BigDecimal("50.00"), null).getPreset()).isEqualTo("50");
        TopUpForm custom = TopUpForm.prefilled(new BigDecimal("12.341"), "/orders/1");
        assertThat(custom.getCustomAmount()).isEqualByComparingTo("12.35");
        assertThat(custom.getReturnTo()).isEqualTo("/orders/1");
        assertThat(TopUpForm.prefilled(new BigDecimal("1.20"), null).getCustomAmount()).isEqualByComparingTo("5.00");
        assertThat(TopUpForm.prefilled(new BigDecimal("5000"), null).getCustomAmount()).isEqualByComparingTo("1000.00");
        assertThat(TopUpForm.prefilled(null, "//evil.example").getReturnTo()).isNull();
        assertThat(TopUpForm.prefilled(null, null).getPreset()).isEqualTo(TopUpForm.DEFAULT_PRESET);
    }

    // -------------------------------------------------------------- withdraw

    @Test
    void validWithdrawHasNoErrors() {
        assertThat(invalidFields(validWithdraw())).isEmpty();
    }

    @Test
    void withdrawChecksAmountHolderAndIban() {
        WithdrawForm form = validWithdraw();
        form.setAmount(new BigDecimal("0.99"));
        form.setAccountHolder(null);
        form.setIban("LT13 1000 0111 0100 1000");
        assertThat(invalidFields(form)).containsExactlyInAnyOrder("amount", "accountHolder", "iban");
    }

    @Test
    void withdrawNeedsAmount() {
        WithdrawForm form = validWithdraw();
        form.setAmount(null);
        assertThat(invalidFields(form)).containsExactly("amount");
    }
}
