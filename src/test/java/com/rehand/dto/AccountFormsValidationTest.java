package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Bean Validation of the account forms, without a Spring context. */
class AccountFormsValidationTest {

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

    private static RegisterForm validRegisterForm() {
        RegisterForm form = new RegisterForm();
        form.setEmail("gaizka@example.com");
        form.setUsername("gaizka_b");
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret123");
        form.setCity("Kaunas");
        form.setAcceptTerms(true);
        return form;
    }

    /** Names of the properties that have at least one violation. */
    private static Set<String> invalidFields(Object form) {
        return validator.validate(form).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static List<String> messages(Object form, String field) {
        return validator.validate(form).stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    // ------------------------------------------------------------ RegisterForm

    @Test
    void aCompleteRegistrationIsValid() {
        assertThat(validator.validate(validRegisterForm())).isEmpty();
    }

    @Test
    void cityIsOptional() {
        RegisterForm form = validRegisterForm();
        form.setCity("   ");

        assertThat(form.getCity()).isNull();
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void textFieldsAreTrimmedButPasswordsAreKeptAsTyped() {
        RegisterForm form = validRegisterForm();
        form.setEmail("  gaizka@example.com ");
        form.setUsername("  gaizka_b ");
        form.setPassword(" Secret 123 ");

        assertThat(form.getEmail()).isEqualTo("gaizka@example.com");
        assertThat(form.getUsername()).isEqualTo("gaizka_b");
        assertThat(form.getPassword()).isEqualTo(" Secret 123 ");
    }

    @Test
    void emptyFormGivesExactlyOneMessagePerRequiredField() {
        RegisterForm form = new RegisterForm();
        form.setEmail("");
        form.setUsername("");
        form.setPassword("");
        form.setConfirmPassword("");

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("email", "username", "password", "confirmPassword", "acceptTerms");
        assertThat(messages(form, "email")).hasSize(1);
        assertThat(messages(form, "username")).hasSize(1);
        assertThat(messages(form, "password")).hasSize(1);
    }

    @Test
    void emailMustLookLikeAnAddressWithADomainDot() {
        for (String bad : List.of("plainaddress", "name@", "@example.com", "name@example", "na me@example.com")) {
            RegisterForm form = validRegisterForm();
            form.setEmail(bad);
            assertThat(invalidFields(form)).as(bad).containsExactly("email");
        }
    }

    @Test
    void usernameNeedsThreeToFortyAllowedCharacters() {
        for (String bad : List.of("ab", "a".repeat(41), "with space", "semi;colon", "emojiñ")) {
            RegisterForm form = validRegisterForm();
            form.setUsername(bad);
            assertThat(invalidFields(form)).as(bad).containsExactly("username");
        }
        for (String good : List.of("abc", "a".repeat(40), "a.b_c-d", "User123")) {
            RegisterForm form = validRegisterForm();
            form.setUsername(good);
            assertThat(validator.validate(form)).as(good).isEmpty();
        }
    }

    @Test
    void passwordNeedsEightCharactersWithALetterAndADigit() {
        for (String bad : List.of("short1A", "onlyletters", "12345678", "a1" + "x".repeat(71))) {
            RegisterForm form = validRegisterForm();
            form.setPassword(bad);
            form.setConfirmPassword(bad);
            assertThat(invalidFields(form)).as(bad).containsExactly("password");
        }
    }

    @Test
    void confirmationMustMatchAndTheErrorIsShownOnTheConfirmField() {
        RegisterForm form = validRegisterForm();
        form.setConfirmPassword("Secret124");

        assertThat(invalidFields(form)).containsExactly("confirmPassword");
        assertThat(messages(form, "confirmPassword")).containsExactly("The two passwords do not match.");
    }

    @Test
    void termsMustBeAccepted() {
        RegisterForm form = validRegisterForm();
        form.setAcceptTerms(false);

        assertThat(invalidFields(form)).containsExactly("acceptTerms");
    }

    @Test
    void cityCannotExceedEightyCharacters() {
        RegisterForm form = validRegisterForm();
        form.setCity("k".repeat(81));

        assertThat(invalidFields(form)).containsExactly("city");
    }

    // ------------------------------------------------------------- ProfileForm

    @Test
    void profileFormAcceptsBioUpToFiveHundredCharacters() {
        ProfileForm form = new ProfileForm("mikel_b", "Vilnius", "b".repeat(500));

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void profileFormRejectsALongBioAndABadUsername() {
        ProfileForm form = new ProfileForm("x", null, "b".repeat(501));

        assertThat(invalidFields(form)).containsExactlyInAnyOrder("bio", "username");
    }

    @Test
    void profileBioLineBreaksCountAsOneCharacter() {
        ProfileForm form = new ProfileForm("mikel_b", null, null);
        form.setBio("line one\r\nline two");

        assertThat(form.getBio()).isEqualTo("line one\nline two");
    }

    // ------------------------------------------------------------ PasswordForm

    private static PasswordForm passwordForm(String current, String next, String confirm) {
        PasswordForm form = new PasswordForm();
        form.setCurrentPassword(current);
        form.setNewPassword(next);
        form.setConfirmNewPassword(confirm);
        return form;
    }

    @Test
    void passwordFormAcceptsAStrongMatchingPassword() {
        assertThat(validator.validate(passwordForm("Old12345", "NewSecret9", "NewSecret9"))).isEmpty();
    }

    @Test
    void passwordFormNeedsTheCurrentPassword() {
        assertThat(invalidFields(passwordForm("", "NewSecret9", "NewSecret9"))).containsExactly("currentPassword");
    }

    @Test
    void passwordFormAppliesTheSameStrengthRules() {
        assertThat(invalidFields(passwordForm("Old12345", "weak", "weak"))).containsExactly("newPassword");
        assertThat(invalidFields(passwordForm("Old12345", "NoDigitsHere", "NoDigitsHere"))).containsExactly("newPassword");
    }

    @Test
    void passwordFormConfirmationMustMatch() {
        PasswordForm form = passwordForm("Old12345", "NewSecret9", "NewSecret8");

        assertThat(invalidFields(form)).containsExactly("confirmNewPassword");
    }
}
