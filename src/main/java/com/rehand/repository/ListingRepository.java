package com.rehand.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rehand.domain.ItemCondition;
import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /**
     * WHERE clause of the public search. Every parameter is always bound to a non-null value
     * (the service passes "%" or a wide range when a filter is off), so no "param IS NULL" tricks
     * are needed. The escape character is "!" because a backslash is awkward in MySQL string literals.
     */
    String SEARCH_WHERE = """
            where l.status in :statuses
              and l.price >= :minPrice and l.price <= :maxPrice
              and (:allCategories = true or l.category.id in :categoryIds)
              and (:allConditions = true or l.condition in :conditions)
              and lower(l.city) like :city escape '!'
              and lower(coalesce(l.itemSize, '')) like :itemSize escape '!'
              and lower(coalesce(l.color, '')) like :color escape '!'
              and lower(coalesce(l.brand, '')) like :brand escape '!'
              and (lower(l.title) like :t1 escape '!' or lower(l.description) like :t1 escape '!'
                   or lower(coalesce(l.brand, '')) like :t1 escape '!')
              and (lower(l.title) like :t2 escape '!' or lower(l.description) like :t2 escape '!'
                   or lower(coalesce(l.brand, '')) like :t2 escape '!')
              and (lower(l.title) like :t3 escape '!' or lower(l.description) like :t3 escape '!'
                   or lower(coalesce(l.brand, '')) like :t3 escape '!')
            """;

    @Query(value = "select l from Listing l " + SEARCH_WHERE,
            countQuery = "select count(l) from Listing l " + SEARCH_WHERE)
    Page<Listing> search(@Param("statuses") Collection<ListingStatus> statuses,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("allCategories") boolean allCategories,
                         @Param("categoryIds") Collection<Long> categoryIds,
                         @Param("allConditions") boolean allConditions,
                         @Param("conditions") Collection<ItemCondition> conditions,
                         @Param("city") String city,
                         @Param("itemSize") String itemSize,
                         @Param("color") String color,
                         @Param("brand") String brand,
                         @Param("t1") String t1,
                         @Param("t2") String t2,
                         @Param("t3") String t3,
                         Pageable pageable);

    /** Seller, category and its parent in one query, for the detail and edit pages. Photos load lazily. */
    @Query("select l from Listing l join fetch l.seller left join fetch l.category c left join fetch c.parent "
            + "where l.id = :id")
    Optional<Listing> findDetailById(@Param("id") Long id);

    /**
     * Loads the listing with a row lock (SELECT ... FOR UPDATE), so two buyers who check out at the
     * same moment are served one after the other: the second one sees SOLD. Only the listings row is
     * locked; the seller loads lazily. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") Long id);

    /** Newest listings with one of the given statuses (home page, related listings). */
    Page<Listing> findByStatusIn(Collection<ListingStatus> statuses, Pageable pageable);

    /** Other listings of the same categories, newest first (the caller removes the current one). */
    List<Listing> findTop5ByCategoryIdAndStatusOrderByCreatedAtDesc(Long categoryId, ListingStatus status);

    Page<Listing> findBySellerIdAndStatusIn(Long sellerId, Collection<ListingStatus> statuses, Pageable pageable);

    long countBySellerIdAndStatus(Long sellerId, ListingStatus status);

    /** Used by the search suggestions: title contains the text (Spring Data escapes % and _). */
    List<Listing> findTop6ByStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(ListingStatus status, String text);

    /** Active listing count per category id, as rows of {categoryId, count}. */
    @Query("select l.category.id, count(l) from Listing l where l.status = :status group by l.category.id")
    List<Object[]> countByCategory(@Param("status") ListingStatus status);

    @Query("select distinct l.city from Listing l where l.status in :statuses order by l.city")
    List<String> findCities(@Param("statuses") Collection<ListingStatus> statuses);

    /** Atomic counter update: concurrent visitors never lose a view. */
    @Modifying
    @Query("update Listing l set l.viewCount = l.viewCount + 1 where l.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
