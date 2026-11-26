package com.rehand.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One photo of a listing (table {@code listing_photos}). {@code sortIndex} is the column
 * {@code position} (0 is the cover); the Java name avoids the HQL keyword POSITION.
 */
@Entity
@Table(name = "listing_photos")
public class ListingPhoto {

    /** Stored paths that start with this prefix are bundled demo images, not uploads. */
    public static final String DEMO_PREFIX = "demo/";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "position", nullable = false)
    private int sortIndex;

    public ListingPhoto() {
        // required by JPA
    }

    public ListingPhoto(Listing listing, String filePath, int sortIndex) {
        this.listing = listing;
        this.filePath = filePath;
        this.sortIndex = sortIndex;
    }

    /** Public URL (without context path): demo images live in static/img/demo, uploads under /uploads. */
    public static String urlFor(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        if (storedPath.startsWith(DEMO_PREFIX)) {
            return "/img/demo/" + storedPath.substring(DEMO_PREFIX.length());
        }
        return "/uploads/" + storedPath;
    }

    public boolean isDemo() {
        return filePath != null && filePath.startsWith(DEMO_PREFIX);
    }

    public String getUrl() {
        return urlFor(filePath);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Listing getListing() {
        return listing;
    }

    public void setListing(Listing listing) {
        this.listing = listing;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }
}
