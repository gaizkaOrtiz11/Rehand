package com.rehand.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rehand.domain.ListingPhoto;

/** Photos are normally reached through {@code Listing.getPhotos()}; this interface exists for completeness. */
public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {
}
