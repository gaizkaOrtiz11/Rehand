package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.rehand.domain.ItemCondition;

/** Defaults, sort mapping, price bounds and URL building of {@link ListingSearchCriteria}. */
class ListingSearchCriteriaTest {

    @Test
    void defaultsMeanNoFilterNewestFirstFirstPage() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        assertThat(c.getPage()).isEqualTo(1);
        assertThat(c.getPageIndex()).isZero();
        assertThat(c.getSortKey()).isEqualTo("newest");
        assertThat(c.isFiltered()).isFalse();
        assertThat(c.isIncludeUnavailable()).isFalse();
        assertThat(c.getConditions()).isEmpty();
        assertThat(c.getTerms()).isEmpty();
        assertThat(c.getEffectiveMin()).isEqualByComparingTo("0");
        assertThat(c.getEffectiveMax()).isEqualByComparingTo("99999999.99");
        assertThat(c.toParams()).isEmpty();
        assertThat(c.pageLinkPrefix()).isEmpty();
        assertThat(ListingSearchCriteria.PAGE_SIZE).isEqualTo(12);
    }

    @Test
    void pageNumbersBelowOneBecomeTheFirstPage() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setPage(0);
        assertThat(c.getPageIndex()).isZero();
        c.setPage(-4);
        assertThat(c.getPageIndex()).isZero();
        c.setPage(3);
        assertThat(c.getPageIndex()).isEqualTo(2);
    }

    @Test
    void sortKeysMapToSpringSortWithIdAsTieBreaker() {
        ListingSearchCriteria c = new ListingSearchCriteria();

        Sort newest = c.toSort();
        assertThat(newest.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(newest.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);

        c.setSort("price_asc");
        assertThat(c.toSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(c.toSort().getOrderFor("id")).isNotNull();

        c.setSort("price_desc");
        assertThat(c.toSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);

        c.setSort("popular");
        assertThat(c.toSort().getOrderFor("viewCount").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(c.toSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void unknownSortKeyFallsBackToNewest() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setSort("drop table listings");
        assertThat(c.getSortKey()).isEqualTo("newest");
        assertThat(c.toSort().getOrderFor("createdAt")).isNotNull();
        c.setSort(null);
        assertThat(c.getSortKey()).isEqualTo("newest");
        assertThat(c.toParams()).isEmpty();
    }

    @Test
    void searchWordsAreLowerCasedAndLimitedToThree() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setQ("  Red   JACKET wool extra words ");
        assertThat(c.getTerms()).containsExactly("red", "jacket", "wool");
        c.setQ("   ");
        assertThat(c.getTerms()).isEmpty();
        c.setQ(null);
        assertThat(c.getTerms()).isEmpty();
    }

    @Test
    void priceBoundsAreSanitised() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setMinPrice(new BigDecimal("-5"));
        c.setMaxPrice(new BigDecimal("-1"));
        assertThat(c.getEffectiveMin()).isEqualByComparingTo("0");
        assertThat(c.getEffectiveMax()).isEqualByComparingTo("99999999.99");

        c.setMinPrice(new BigDecimal("50"));
        c.setMaxPrice(new BigDecimal("10"));
        // reversed bounds are swapped
        assertThat(c.getEffectiveMin()).isEqualByComparingTo("10");
        assertThat(c.getEffectiveMax()).isEqualByComparingTo("50");

        c.setMinPrice(new BigDecimal("10"));
        c.setMaxPrice(new BigDecimal("50"));
        assertThat(c.getEffectiveMin()).isEqualByComparingTo("10");
        assertThat(c.getEffectiveMax()).isEqualByComparingTo("50");
    }

    @Test
    void anyFilterMakesTheSearchFiltered() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setSort("price_asc");
        c.setPage(4);
        assertThat(c.isFiltered()).isFalse();
        c.setCity("Kaunas");
        assertThat(c.isFiltered()).isTrue();

        ListingSearchCriteria d = new ListingSearchCriteria();
        d.setConditions(List.of(ItemCondition.NEW));
        assertThat(d.isFiltered()).isTrue();

        ListingSearchCriteria e = new ListingSearchCriteria();
        e.setIncludeUnavailable(true);
        assertThat(e.isFiltered()).isTrue();
    }

    private static ListingSearchCriteria sample() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setQ("red jacket");
        c.setCategory("shoes");
        c.setMinPrice(new BigDecimal("5"));
        c.setConditions(List.of(ItemCondition.NEW, ItemCondition.GOOD));
        c.setSort("price_desc");
        return c;
    }

    @Test
    void queryStringKeepsFiltersInOrderAndEncodesThem() {
        assertThat(sample().queryString(null, null))
                .isEqualTo("q=red%20jacket&category=shoes&minPrice=5&conditions=NEW&conditions=GOOD&sort=price_desc");
        assertThat(sample().pageLinkPrefix())
                .isEqualTo("q=red%20jacket&category=shoes&minPrice=5&conditions=NEW&conditions=GOOD&sort=price_desc&");
    }

    @Test
    void queryStringCanLeaveOutOneFilterOrOneValue() {
        assertThat(sample().queryString("category", null))
                .isEqualTo("q=red%20jacket&minPrice=5&conditions=NEW&conditions=GOOD&sort=price_desc");
        assertThat(sample().queryString("conditions", "NEW"))
                .isEqualTo("q=red%20jacket&category=shoes&minPrice=5&conditions=GOOD&sort=price_desc");
        assertThat(sample().paramsWithout("sort")).hasSize(5);
    }

    @Test
    void chipsOfferARemoveLinkPerActiveFilter() {
        List<FilterChip> chips = sample().activeChips("Shoes", c -> c == ItemCondition.NEW ? "New" : "Good");
        assertThat(chips).extracting(FilterChip::getLabel)
                .containsExactly("Search: red jacket", "Shoes", "From €5", "New", "Good");
        assertThat(chips.get(1).getRemoveUrl())
                .isEqualTo("/listings?q=red%20jacket&minPrice=5&conditions=NEW&conditions=GOOD&sort=price_desc");
        assertThat(chips.get(3).getRemoveUrl()).doesNotContain("conditions=NEW").contains("conditions=GOOD");
    }

    @Test
    void removingTheLastFilterLeadsToThePlainListingsUrl() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setCity("Kaunas");
        List<FilterChip> chips = c.activeChips(null, ItemCondition::name);
        assertThat(chips).hasSize(1);
        assertThat(chips.get(0).getLabel()).isEqualTo("City: Kaunas");
        assertThat(chips.get(0).getRemoveUrl()).isEqualTo("/listings");
    }

    @Test
    void priceRangeIsOneChipThatRemovesBothBounds() {
        ListingSearchCriteria c = new ListingSearchCriteria();
        c.setMinPrice(new BigDecimal("10"));
        c.setMaxPrice(new BigDecimal("40"));
        c.setBrand("Nike");
        List<FilterChip> chips = c.activeChips(null, ItemCondition::name);
        assertThat(chips.get(0).getLabel()).isEqualTo("€10 to €40");
        assertThat(chips.get(0).getRemoveUrl()).isEqualTo("/listings?brand=Nike");
    }
}
