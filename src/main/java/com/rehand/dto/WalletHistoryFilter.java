package com.rehand.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriUtils;

import com.rehand.domain.TransactionType;

/**
 * GET filter of the wallet history: {@code type}, {@code from}, {@code to} (yyyy-MM-dd, both inclusive) and
 * {@code page} (1-based). Unknown values bind as null, so a hand-edited URL just shows everything.
 */
public class WalletHistoryFilter {

    private TransactionType type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private int page = 1;

    public WalletHistoryFilter() {
    }

    public WalletHistoryFilter(TransactionType type, LocalDate from, LocalDate to) {
        this.type = type;
        this.from = from;
        this.to = to;
    }

    /** First day included, after swapping reversed bounds. */
    public LocalDate getStart() {
        if (from != null && to != null && from.isAfter(to)) {
            return to;
        }
        return from;
    }

    /** Last day included, after swapping reversed bounds. */
    public LocalDate getEnd() {
        if (from != null && to != null && from.isAfter(to)) {
            return from;
        }
        return to;
    }

    public boolean isActive() {
        return type != null || from != null || to != null;
    }

    /** 0-based page index for Spring Data. */
    public int getPageIndex() {
        return Math.max(page, 1) - 1;
    }

    /** Query string of the filter followed by "&amp;" (or empty), ready for "page=N" to be appended. */
    public String pageLinkPrefix() {
        List<String> parts = new ArrayList<>();
        if (type != null) {
            parts.add("type=" + type.name());
        }
        if (from != null) {
            parts.add("from=" + UriUtils.encodeQueryParam(from.toString(), StandardCharsets.UTF_8));
        }
        if (to != null) {
            parts.add("to=" + UriUtils.encodeQueryParam(to.toString(), StandardCharsets.UTF_8));
        }
        return parts.isEmpty() ? "" : String.join("&", parts) + "&";
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }
}
