package com.rehand.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.rehand.exception.BusinessRuleException;

/** State changes and photo helpers on the {@link Listing} entity (no database). */
class ListingTest {

    private static Listing withPhotos(int n) {
        Listing l = new Listing();
        for (int i = 0; i < n; i++) {
            l.addPhoto("listings/p" + i + ".jpg").setId(10L + i);
        }
        return l;
    }

    @Test
    void newListingsStartActiveWithoutPhotos() {
        Listing l = new Listing();
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(l.getFirstPhotoPath()).isNull();
        assertThat(l.getFirstPhotoUrl()).isNull();
        assertThat(l.isEditable()).isTrue();
    }

    @Test
    void hideAndUnhideAreTheOnlyManualTransitions() {
        Listing l = new Listing();
        l.hide();
        assertThat(l.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        assertThatThrownBy(l::hide).isInstanceOf(BusinessRuleException.class);
        l.unhide();
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThatThrownBy(l::unhide).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markRemovedWorksForActiveAndHiddenOnly() {
        Listing active = new Listing();
        active.markRemoved();
        assertThat(active.getStatus()).isEqualTo(ListingStatus.REMOVED);
        assertThat(active.isEditable()).isFalse();

        for (ListingStatus s : new ListingStatus[] {ListingStatus.SOLD, ListingStatus.RESERVED}) {
            Listing l = new Listing();
            l.setStatus(s);
            assertThatThrownBy(l::markRemoved).isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void addedPhotosGetConsecutivePositions() {
        Listing l = withPhotos(3);
        assertThat(l.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1, 2);
        assertThat(l.getFirstPhotoPath()).isEqualTo("listings/p0.jpg");
        assertThat(l.getPhotos().get(1).getListing()).isSameAs(l);
    }

    @Test
    void makeCoverReordersAndRenumbers() {
        Listing l = withPhotos(3);
        assertThat(l.makeCover(12L)).isTrue();
        assertThat(l.getPhotos()).extracting(ListingPhoto::getId).containsExactly(12L, 10L, 11L);
        assertThat(l.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1, 2);
        assertThat(l.makeCover(999L)).isFalse();
    }

    @Test
    void removePhotoRenumbersTheRest() {
        Listing l = withPhotos(3);
        assertThat(l.removePhoto(10L).getFilePath()).isEqualTo("listings/p0.jpg");
        assertThat(l.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1);
        assertThat(l.getFirstPhotoPath()).isEqualTo("listings/p1.jpg");
        assertThat(l.removePhoto(999L)).isNull();
    }

    @Test
    void demoPhotosAreServedFromTheStaticFolderAndUploadsFromTheUploadFolder() {
        assertThat(ListingPhoto.urlFor("demo/jacket.svg")).isEqualTo("/img/demo/jacket.svg");
        assertThat(ListingPhoto.urlFor("listings/abc.jpg")).isEqualTo("/uploads/listings/abc.jpg");
        assertThat(ListingPhoto.urlFor(null)).isNull();
        Listing l = new Listing();
        l.addPhoto("demo/lamp.svg");
        assertThat(l.getFirstPhotoPath()).isEqualTo("demo/lamp.svg");
        assertThat(l.getFirstPhotoUrl()).isEqualTo("/img/demo/lamp.svg");
        assertThat(l.getPhotos().get(0).isDemo()).isTrue();
    }

    @Test
    void conditionToneChoosesTheBadgeColour() {
        Listing l = new Listing();
        l.setCondition(ItemCondition.NEW_WITH_TAGS);
        assertThat(l.getConditionTone()).isEqualTo("accent");
        l.setCondition(ItemCondition.NEW);
        assertThat(l.getConditionTone()).isEqualTo("accent");
        l.setCondition(ItemCondition.GOOD);
        assertThat(l.getConditionTone()).isEqualTo("neutral");
        l.setCondition(ItemCondition.FAIR);
        assertThat(l.getConditionTone()).isEqualTo("warning");
    }
}
