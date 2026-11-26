package com.rehand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** "Mark as shipped": the carrier's tracking code (4 to 60 characters). */
public class ShipForm {

    @NotBlank(message = "Enter the tracking code from the carrier.")
    @Size(min = 4, max = 60, message = "The tracking code must have 4 to 60 characters.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ./-]*$", message = "Use letters, digits, spaces, dots, slashes or hyphens.")
    private String trackingCode;

    public String getTrackingCode() {
        return trackingCode;
    }

    public void setTrackingCode(String trackingCode) {
        this.trackingCode = FormText.trimToNull(trackingCode);
    }
}
