package com.rehand.dto;

import jakarta.validation.constraints.Size;

/**
 * "Cancel order": an optional reason. The orders table has no column for it, so it only travels
 * in the notification to the other party.
 */
public class CancelForm {

    public static final int REASON_MAX = 200;

    @Size(max = REASON_MAX, message = "The reason can have at most 200 characters.")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = FormText.trimToNull(reason == null ? null : reason.replace("\r\n", "\n"));
    }
}
