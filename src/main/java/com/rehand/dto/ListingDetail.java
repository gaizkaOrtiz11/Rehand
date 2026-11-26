package com.rehand.dto;

import java.util.List;

import com.rehand.domain.Listing;

/**
 * Everything the detail page shows, loaded in one transaction so the template never touches
 * a lazy association: the listing (photos initialised), the category breadcrumb, related
 * listings, the seller's number of available items and what the viewer may do.
 */
public class ListingDetail {

    private final Listing listing;
    private final List<String[]> breadcrumb;
    private final List<Listing> related;
    private final long sellerActiveCount;
    private final boolean owner;
    private final boolean canManage;
    private final int views;

    public ListingDetail(Listing listing, List<String[]> breadcrumb, List<Listing> related,
                         long sellerActiveCount, boolean owner, boolean canManage, int views) {
        this.listing = listing;
        this.breadcrumb = breadcrumb;
        this.related = related;
        this.sellerActiveCount = sellerActiveCount;
        this.owner = owner;
        this.canManage = canManage;
        this.views = views;
    }

    public Listing getListing() {
        return listing;
    }

    /** Category trail from the top level down; each entry is {name, slug}. */
    public List<String[]> getBreadcrumb() {
        return breadcrumb;
    }

    public List<Listing> getRelated() {
        return related;
    }

    public long getSellerActiveCount() {
        return sellerActiveCount;
    }

    public boolean isOwner() {
        return owner;
    }

    /** Owner or admin: may edit, hide and delete. */
    public boolean isCanManage() {
        return canManage;
    }

    public int getViews() {
        return views;
    }

    public String getSellerInitials() {
        return Initials.of(listing.getSeller().getUsername());
    }
}
