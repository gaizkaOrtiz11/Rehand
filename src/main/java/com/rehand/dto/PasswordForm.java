package com.rehand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Backing object of the "Change password" form. The current password is checked by the service. */
@FieldsMatch(first = "newPassword", second = "confirmNewPassword", message = "The two new passwords do not match.")
public class PasswordForm {

    @NotBlank(message = "Enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Choose a new password.")
    @Size(min = 8, max = 72, message = "The password must have 8 to 72 characters.")
    @Pattern(regexp = RegisterForm.PASSWORD_PATTERN, message = "The password needs at least one letter and one digit.")
    private String newPassword;

    @NotBlank(message = "Repeat the new password.")
    private String confirmNewPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = FormText.emptyToNull(currentPassword);
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = FormText.emptyToNull(newPassword);
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }

    public void setConfirmNewPassword(String confirmNewPassword) {
        this.confirmNewPassword = FormText.emptyToNull(confirmNewPassword);
    }
}
