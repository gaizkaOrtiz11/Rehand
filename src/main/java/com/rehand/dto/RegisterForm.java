package com.rehand.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Backing object of the registration form. Text fields are trimmed and an empty value becomes
 * {@code null}, so a missing field produces exactly one error message ("required").
 */
@FieldsMatch(first = "password", second = "confirmPassword", message = "The two passwords do not match.")
public class RegisterForm {

    /** At least one letter and one digit. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9]).*$";
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]*$";
    public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    @NotBlank(message = "Enter your email address.")
    @Size(max = 255, message = "The email address can have at most 255 characters.")
    @Email(regexp = EMAIL_PATTERN, message = "Enter a valid email address, for example name@example.com.")
    private String email;

    @NotBlank(message = "Choose a username.")
    @Size(min = 3, max = 40, message = "The username must have 3 to 40 characters.")
    @Pattern(regexp = USERNAME_PATTERN, message = "Use only letters, digits, dots, underscores and hyphens.")
    private String username;

    @NotBlank(message = "Choose a password.")
    @Size(min = 8, max = 72, message = "The password must have 8 to 72 characters.")
    @Pattern(regexp = PASSWORD_PATTERN, message = "The password needs at least one letter and one digit.")
    private String password;

    @NotBlank(message = "Repeat the password.")
    private String confirmPassword;

    @Size(max = 80, message = "The city can have at most 80 characters.")
    private String city;

    @AssertTrue(message = "You must accept the community rules to create an account.")
    private boolean acceptTerms;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = FormText.trimToNull(email);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = FormText.trimToNull(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = FormText.emptyToNull(password);
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = FormText.emptyToNull(confirmPassword);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = FormText.trimToNull(city);
    }

    public boolean isAcceptTerms() {
        return acceptTerms;
    }

    public void setAcceptTerms(boolean acceptTerms) {
        this.acceptTerms = acceptTerms;
    }
}
