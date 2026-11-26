package com.rehand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Backing object of the "Edit profile" form. */
public class ProfileForm {

    @NotBlank(message = "Choose a username.")
    @Size(min = 3, max = 40, message = "The username must have 3 to 40 characters.")
    @Pattern(regexp = RegisterForm.USERNAME_PATTERN, message = "Use only letters, digits, dots, underscores and hyphens.")
    private String username;

    @Size(max = 80, message = "The city can have at most 80 characters.")
    private String city;

    @Size(max = 500, message = "The bio can have at most 500 characters.")
    private String bio;

    public ProfileForm() {
    }

    public ProfileForm(String username, String city, String bio) {
        this.username = username;
        this.city = city;
        this.bio = bio;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = FormText.trimToNull(username);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = FormText.trimToNull(city);
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = FormText.trimToNull(bio == null ? null : bio.replace("\r\n", "\n"));
    }
}
