package com.rehand.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.rehand.exception.BusinessRuleException;

/**
 * An item for sale (table {@code listings}). State changes live here as methods
 * ({@link #hide()}, {@link #unhide()}, {@link #markRemoved()}, {@link #makeCover(Long)},
 * {@link #markSold()}, {@link #reopen()}),
 * so an illegal change throws before anything is saved.
 *
 * Mapping notes: {@code condition} is the column {@code item_condition}; {@code itemSize} is the
 * column {@code size} (the Java name keeps the HQL function name SIZE out of queries).
 */
@Entity
@Table(name = "listings")
public class Listing {

    public static final int MAX_PHOTOS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "item_condition", nullable = false, length = 20)
    private ItemCondition condition;

    @Column(name = "brand", length = 60)
    private String brand;

    @Column(name = "size", length = 20)
    private String itemSize;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortIndex ASC")
    @BatchSize(size = 24)
    private List<ListingPhoto> photos = new ArrayList<>();

    public Listing() {
        // required by JPA
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------ rules

    public boolean isOwnedBy(Long userId) {
        return userId != null && seller != null && userId.equals(seller.getId());
    }

    /** ACTIVE to HIDDEN. */
    public void hide() {
        if (status != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("Only available listings can be hidden.");
        }
        status = ListingStatus.HIDDEN;
    }

    /** HIDDEN to ACTIVE. */
    public void unhide() {
        if (status != ListingStatus.HIDDEN) {
            throw new BusinessRuleException("Only hidden listings can be made available again.");
        }
        status = ListingStatus.ACTIVE;
    }

    /** Soft delete: the row stays (orders and chats keep pointing at it), the status becomes REMOVED. */
    public void markRemoved() {
        if (status == ListingStatus.SOLD) {
            throw new BusinessRuleException("A sold listing cannot be deleted.");
        }
        if (status == ListingStatus.RESERVED) {
            throw new BusinessRuleException("A reserved listing cannot be deleted until the reservation ends.");
        }
        status = ListingStatus.REMOVED;
    }

    /**
     * True when this user may buy the listing right now: it is ACTIVE, the user is not the seller and
     * neither side is banned. A RESERVED listing is never purchasable through this method.
     */
    public boolean canBePurchasedBy(User buyer) {
        return canBePurchasedBy(buyer, false);
    }

    /**
     * Same as {@link #canBePurchasedBy(User)}, but a RESERVED listing is also purchasable when
     * {@code holdsReservation} is true. Only the service knows the offer, so it passes true only for the
     * buyer of the accepted, unexpired, unused offer that reserved this listing.
     */
    public boolean canBePurchasedBy(User buyer, boolean holdsReservation) {
        boolean available = status == ListingStatus.ACTIVE
                || (status == ListingStatus.RESERVED && holdsReservation);
        return buyer != null && available && !isOwnedBy(buyer.getId())
                && !buyer.isBanned() && seller != null && !seller.isBanned();
    }

    /** ACTIVE to RESERVED: the seller accepted an offer. */
    public void reserve() {
        if (status != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("This item is no longer available.");
        }
        status = ListingStatus.RESERVED;
    }

    /** RESERVED to ACTIVE: the accepted offer expired without a checkout. */
    public void releaseReservation() {
        if (status != ListingStatus.RESERVED) {
            throw new BusinessRuleException("Only a reserved listing can be released.");
        }
        status = ListingStatus.ACTIVE;
    }

    /** ACTIVE (or RESERVED, from step 6 on) to SOLD: an order was placed. */
    public void markSold() {
        if (status != ListingStatus.ACTIVE && status != ListingStatus.RESERVED) {
            throw new BusinessRuleException("This item is no longer available.");
        }
        status = ListingStatus.SOLD;
    }

    /** SOLD to ACTIVE: the order was cancelled, so the item is for sale again. */
    public void reopen() {
        if (status != ListingStatus.SOLD) {
            throw new BusinessRuleException("Only a sold listing can be put back on sale.");
        }
        status = ListingStatus.ACTIVE;
    }

    /** True while the owner may still change the text and the photos. */
    public boolean isEditable() {
        return status == ListingStatus.ACTIVE || status == ListingStatus.HIDDEN || status == ListingStatus.RESERVED;
    }

    // ------------------------------------------------------------ photos

    /** Appends a photo at the end of the list and returns it. */
    public ListingPhoto addPhoto(String filePath) {
        ListingPhoto photo = new ListingPhoto(this, filePath, photos.size());
        photos.add(photo);
        return photo;
    }

    /** Removes a photo (the row is deleted through orphanRemoval) and closes the gap in the positions. */
    public ListingPhoto removePhoto(Long photoId) {
        Iterator<ListingPhoto> it = photos.iterator();
        while (it.hasNext()) {
            ListingPhoto p = it.next();
            if (p.getId() != null && p.getId().equals(photoId)) {
                it.remove();
                renumberPhotos();
                return p;
            }
        }
        return null;
    }

    /** Moves the photo to position 0 and renumbers the others in their old order. Returns false when not found. */
    public boolean makeCover(Long photoId) {
        for (int i = 0; i < photos.size(); i++) {
            ListingPhoto p = photos.get(i);
            if (p.getId() != null && p.getId().equals(photoId)) {
                photos.remove(i);
                photos.add(0, p);
                renumberPhotos();
                return true;
            }
        }
        return false;
    }

    private void renumberPhotos() {
        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setSortIndex(i);
        }
    }

    /** Stored path of the cover photo (relative to the upload folder), or null. Kept raw for listing-card.html. */
    public String getFirstPhotoPath() {
        return photos.isEmpty() ? null : photos.get(0).getFilePath();
    }

    /** Ready-to-use URL of the cover photo (without context path), or null when there is no photo. */
    public String getFirstPhotoUrl() {
        return photos.isEmpty() ? null : photos.get(0).getUrl();
    }

    /** "accent" for new items, "warning" for fair ones, "neutral" otherwise: picks the badge colour. */
    public String getConditionTone() {
        if (condition == ItemCondition.NEW_WITH_TAGS || condition == ItemCondition.NEW) {
            return "accent";
        }
        if (condition == ItemCondition.FAIR) {
            return "warning";
        }
        return "neutral";
    }

    // ------------------------------------------------------------ getters and setters

    public Long getId() {
        return id;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getItemSize() {
        return itemSize;
    }

    public void setItemSize(String itemSize) {
        this.itemSize = itemSize;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public int getViewCount() {
        return viewCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ListingPhoto> getPhotos() {
        return photos;
    }
}
