package com.rehand.dto;

import java.time.Duration;
import java.time.LocalDateTime;

/** Short "time left" labels for offer deadlines: "41 h", "35 min", "less than a minute". */
public final class TimeLeft {

    private TimeLeft() {
    }

    /** Label for the time between now and the deadline, or "expired" when it has passed. */
    public static String label(LocalDateTime now, LocalDateTime deadline) {
        if (now == null || deadline == null) {
            return "";
        }
        long minutes = Duration.between(now, deadline).toMinutes();
        if (!now.isBefore(deadline)) {
            return "expired";
        }
        if (minutes < 1) {
            return "less than a minute";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + " h";
    }
}
