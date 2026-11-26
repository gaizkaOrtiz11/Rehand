package com.rehand.dto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriUtils;

import com.rehand.domain.ItemCondition;

/**
 * The filters of the browse page, bound from the query string of {@code GET /listings}
 * (so a search is a bookmarkable URL). All fields are optional.
 *
 * <ul>
 *   <li>{@code q}: up to three words, each must appear in title, description or brand</li>
 *   <li>{@code category}: slug, includes sub-categories</li>
 *   <li>{@code minPrice}, {@code maxPrice}, {@code conditions}, {@code city}, {@code size}, {@code color}, {@code brand}</li>
 *   <li>{@code includeUnavailable}: also show reserved and sold items (default off)</li>
 *   <li>{@code sort}: newest (default), price_asc, price_desc, popular</li>
 *   <li>{@code page}: 1-based; the page size is fixed at {@value #PAGE_SIZE} ({@code size} is the item size filter)</li>
 * </ul>
 */
public class ListingSearchCriteria {

    public static final int PAGE_SIZE = 12;
    public static final String DEFAULT_SORT = "newest";

    /** Sort keys and their labels, in the order of the drop-down. */
    public static final Map<String, String> SORT_OPTIONS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("newest", "Newest first");
        m.put("price_asc", "Price: low to high");
        m.put("price_desc", "Price: high to low");
        m.put("popular", "Most viewed");
        SORT_OPTIONS = Collections.unmodifiableMap(m);
    }

    private String q;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<ItemCondition> conditions = new ArrayList<>();
    private String city;
    private String size;
    private String color;
    private String brand;
    private boolean includeUnavailable;
    private String sort = DEFAULT_SORT;
    private int page = 1;

    // ------------------------------------------------------------ derived values

    /** Zero-based page index for Spring Data. */
    public int getPageIndex() {
        return Math.max(page, 1) - 1;
    }

    /** The sort key if it is known, otherwise "newest". */
    public String getSortKey() {
        return sort != null && SORT_OPTIONS.containsKey(sort) ? sort : DEFAULT_SORT;
    }

    /** Spring Data sort for the current key; the id is the tie-breaker so paging is stable. */
    public Sort toSort() {
        Sort.Order tie = Sort.Order.desc("id");
        switch (getSortKey()) {
            case "price_asc":
                return Sort.by(Sort.Order.asc("price"), tie);
            case "price_desc":
                return Sort.by(Sort.Order.desc("price"), tie);
            case "popular":
                return Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"), tie);
            default:
                return Sort.by(Sort.Order.desc("createdAt"), tie);
        }
    }

    /** At most three search words, lower-case; empty when q is blank. */
    public List<String> getTerms() {
        List<String> terms = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return terms;
        }
        for (String word : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty() && terms.size() < 3) {
                terms.add(word);
            }
        }
        return terms;
    }

    /** The lower price bound, never negative and never above the upper bound. */
    public BigDecimal getEffectiveMin() {
        BigDecimal min = minPrice == null || minPrice.signum() < 0 ? BigDecimal.ZERO : minPrice;
        BigDecimal max = getEffectiveMaxOrNull();
        if (max != null && min.compareTo(max) > 0) {
            return max;
        }
        return min;
    }

    /** The upper price bound, or the largest DECIMAL(10,2) value when no maximum is set. */
    public BigDecimal getEffectiveMax() {
        BigDecimal max = getEffectiveMaxOrNull();
        if (max == null) {
            return new BigDecimal("99999999.99");
        }
        BigDecimal min = minPrice == null || minPrice.signum() < 0 ? BigDecimal.ZERO : minPrice;
        return min.compareTo(max) > 0 ? min : max;
    }

    private BigDecimal getEffectiveMaxOrNull() {
        return maxPrice == null || maxPrice.signum() < 0 ? null : maxPrice;
    }

    /** True when something other than sort and page is set. */
    public boolean isFiltered() {
        return !getTerms().isEmpty() || notBlank(category) || minPrice != null || maxPrice != null
                || !conditions.isEmpty() || notBlank(city) || notBlank(size) || notBlank(color)
                || notBlank(brand) || includeUnavailable;
    }

    // ------------------------------------------------------------ URLs and chips

    /** The filters as ordered name/value pairs, without page; sort only when it is not the default. */
    public List<String[]> toParams() {
        List<String[]> out = new ArrayList<>();
        add(out, "q", q == null ? null : q.trim());
        add(out, "category", category);
        add(out, "minPrice", minPrice == null ? null : minPrice.toPlainString());
        add(out, "maxPrice", maxPrice == null ? null : maxPrice.toPlainString());
        for (ItemCondition c : conditions) {
            add(out, "conditions", c.name());
        }
        add(out, "city", city);
        add(out, "size", size);
        add(out, "color", color);
        add(out, "brand", brand);
        if (includeUnavailable) {
            add(out, "includeUnavailable", "true");
        }
        if (!DEFAULT_SORT.equals(getSortKey())) {
            add(out, "sort", getSortKey());
        }
        return out;
    }

    private static void add(List<String[]> out, String name, String value) {
        if (value != null && !value.isBlank()) {
            out.add(new String[] {name, value});
        }
    }

    /** The same pairs as {@link #toParams()} without the parameters called one of {@code omitNames}. */
    public List<String[]> paramsWithout(String... omitNames) {
        List<String[]> out = new ArrayList<>();
        for (String[] p : toParams()) {
            boolean omit = false;
            for (String n : omitNames) {
                if (p[0].equals(n)) {
                    omit = true;
                }
            }
            if (!omit) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Encoded query string (no leading "?") of the current filters. A pair is left out when its name
     * equals {@code omitName} and ({@code omitValue} is null or equals the value).
     */
    public String queryString(String omitName, String omitValue) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : toParams()) {
            if (p[0].equals(omitName) && (omitValue == null || omitValue.equals(p[1]))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(UriUtils.encodeQueryParam(p[0], StandardCharsets.UTF_8))
                    .append('=')
                    .append(UriUtils.encodeQueryParam(p[1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** Query string prefix for pagination links: "a=b&c=d&" or an empty string, ready for "page=N". */
    public String pageLinkPrefix() {
        String qs = queryString(null, null);
        return qs.isEmpty() ? "" : qs + "&";
    }

    /** Link to the listings page with the given query string (empty means no filters). */
    public static String listingsUrl(String queryString) {
        return queryString.isEmpty() ? "/listings" : "/listings?" + queryString;
    }

    /**
     * One removable chip per active filter.
     *
     * @param categoryLabel   display name of the selected category, or null
     * @param conditionLabels maps a condition to its display text
     */
    public List<FilterChip> activeChips(String categoryLabel, Function<ItemCondition, String> conditionLabels) {
        List<FilterChip> chips = new ArrayList<>();
        if (!getTerms().isEmpty()) {
            chips.add(chip("Search: " + q.trim(), "q", null));
        }
        if (notBlank(category)) {
            chips.add(chip(categoryLabel != null ? categoryLabel : category, "category", null));
        }
        if (minPrice != null || maxPrice != null) {
            String label;
            if (minPrice != null && maxPrice != null) {
                label = "€" + minPrice.toPlainString() + " to €" + maxPrice.toPlainString();
            } else if (minPrice != null) {
                label = "From €" + minPrice.toPlainString();
            } else {
                label = "Up to €" + maxPrice.toPlainString();
            }
            chips.add(new FilterChip(label, listingsUrl(queryStringWithout("minPrice", "maxPrice"))));
        }
        for (ItemCondition c : conditions) {
            chips.add(chip(conditionLabels.apply(c), "conditions", c.name()));
        }
        if (notBlank(city)) {
            chips.add(chip("City: " + city, "city", null));
        }
        if (notBlank(size)) {
            chips.add(chip("Size: " + size, "size", null));
        }
        if (notBlank(color)) {
            chips.add(chip("Color: " + color, "color", null));
        }
        if (notBlank(brand)) {
            chips.add(chip("Brand: " + brand, "brand", null));
        }
        if (includeUnavailable) {
            chips.add(chip("Including reserved and sold", "includeUnavailable", null));
        }
        return chips;
    }

    private FilterChip chip(String label, String name, String value) {
        return new FilterChip(label, listingsUrl(queryString(name, value)));
    }

    private String queryStringWithout(String... names) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : paramsWithout(names)) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(UriUtils.encodeQueryParam(p[0], StandardCharsets.UTF_8))
                    .append('=')
                    .append(UriUtils.encodeQueryParam(p[1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ------------------------------------------------------------ getters and setters

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public List<ItemCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ItemCondition> conditions) {
        // a blank value such as "?conditions=" converts to null: drop it so no code has to guard against it
        this.conditions = new ArrayList<>();
        if (conditions != null) {
            for (ItemCondition c : conditions) {
                if (c != null) {
                    this.conditions.add(c);
                }
            }
        }
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public boolean isIncludeUnavailable() {
        return includeUnavailable;
    }

    public void setIncludeUnavailable(boolean includeUnavailable) {
        this.includeUnavailable = includeUnavailable;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
