package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.rehand.domain.Category;
import com.rehand.domain.ItemCondition;
import com.rehand.domain.Listing;
import com.rehand.domain.ListingPhoto;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.User;
import com.rehand.dto.ListingDetail;
import com.rehand.dto.ListingForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.CategoryRepository;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.UserRepository;

/** Plain JUnit and Mockito: the business rules of {@link ListingService} without Spring or a database. */
class ListingServiceTest {

    private static final long LISTING_ID = 10L;
    private static final long OWNER_ID = 5L;
    private static final long OTHER_ID = 7L;

    private ListingRepository listings;
    private CategoryRepository categories;
    private UserRepository users;
    private FileStorageService storage;
    private OfferService offers;
    private ListingService service;

    private User owner;
    private Category leaf;

    @BeforeEach
    void setUp() {
        listings = mock(ListingRepository.class);
        categories = mock(CategoryRepository.class);
        users = mock(UserRepository.class);
        storage = mock(FileStorageService.class);
        offers = mock(OfferService.class);
        service = new ListingService(listings, categories, users, storage, offers);

        owner = user(OWNER_ID);
        leaf = new Category();
        ReflectionTestUtils.setField(leaf, "id", 11L);
        leaf.setName("Shoes");
        leaf.setSlug("shoes");
        when(categories.findById(11L)).thenReturn(Optional.of(leaf));
    }

    // ------------------------------------------------------------ status changes: who may do what

    @Test
    void ownerCanHideAnActiveListing() {
        Listing l = stored(ListingStatus.ACTIVE);
        service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OWNER_ID, false);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        verify(offers).closeOpenOffers(eq(l), eq(null), any());
    }

    @Test
    void deletingDeclinesTheOpenOffers() {
        Listing l = stored(ListingStatus.ACTIVE);
        service.delete(LISTING_ID, OWNER_ID, false);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.REMOVED);
        verify(offers).closeOpenOffers(eq(l), eq(null), any());
    }

    @Test
    void aReservedListingCannotBeHiddenOrDeletedAndKeepsItsOffers() {
        Listing l = stored(ListingStatus.RESERVED);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.delete(LISTING_ID, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        verify(offers, never()).closeOpenOffers(any(), any(), any());
    }

    @Test
    void adminCanHideSomeoneElsesListing() {
        Listing l = stored(ListingStatus.ACTIVE);
        service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, 99L, true);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.HIDDEN);
    }

    @Test
    void anotherMemberCannotChangeTheListing() {
        Listing l = stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OTHER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void aGuestCannotChangeTheListing() {
        stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, null, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hiddenListingCanBeMadeAvailableAgain() {
        Listing l = stored(ListingStatus.HIDDEN);
        service.changeStatus(LISTING_ID, ListingStatus.ACTIVE, OWNER_ID, false);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void unhidingAnActiveListingIsRefused() {
        stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.ACTIVE, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void soldAndReservedListingsCannotBeHidden() {
        for (ListingStatus status : new ListingStatus[] {ListingStatus.SOLD, ListingStatus.RESERVED}) {
            Listing l = stored(status);
            assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OWNER_ID, false))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(l.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void reservedAndSoldAreNotManualTargets() {
        stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.SOLD, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.RESERVED, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aMissingOrRemovedListingIsNotFound() {
        when(listings.findDetailById(LISTING_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OWNER_ID, false))
                .isInstanceOf(NotFoundException.class);

        stored(ListingStatus.REMOVED);
        assertThatThrownBy(() -> service.changeStatus(LISTING_ID, ListingStatus.HIDDEN, OWNER_ID, true))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------ delete (soft)

    @Test
    void deleteMarksTheListingRemoved() {
        Listing l = stored(ListingStatus.HIDDEN);
        service.delete(LISTING_ID, OWNER_ID, false);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.REMOVED);
    }

    @Test
    void soldAndReservedListingsCannotBeDeleted() {
        for (ListingStatus status : new ListingStatus[] {ListingStatus.SOLD, ListingStatus.RESERVED}) {
            Listing l = stored(status);
            assertThatThrownBy(() -> service.delete(LISTING_ID, OWNER_ID, false))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(l.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void anotherMemberCannotDelete() {
        Listing l = stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.delete(LISTING_ID, OTHER_ID, false)).isInstanceOf(BusinessRuleException.class);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    // ------------------------------------------------------------ publish: photos

    @Test
    void publishNeedsAtLeastOnePhoto() {
        assertThatThrownBy(() -> service.create(validForm(), List.of(), OWNER_ID))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at least one photo");
        // an empty file part (browser sends one when nothing was chosen) does not count
        MultipartFile empty = new MockMultipartFile("photos", "", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> service.create(validForm(), List.of(empty), OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(storage, never()).saveImage(any(), any());
    }

    @Test
    void publishAcceptsEightPhotosButNotNine() {
        assertThatThrownBy(() -> service.create(validForm(), photos(9), OWNER_ID))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("8");
        verify(storage, never()).saveImage(any(), any());

        when(users.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(storage.saveImage(any(MultipartFile.class), eq("listings"))).thenReturn("listings/x.jpg");
        when(listings.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        Listing saved = service.create(validForm(), photos(8), OWNER_ID);
        assertThat(saved.getPhotos()).hasSize(8);
    }

    @Test
    void publishStoresPhotosInOrderAndStartsActive() {
        MultipartFile first = photo("a.jpg");
        MultipartFile second = photo("b.jpg");
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(storage.saveImage(first, "listings")).thenReturn("listings/a.jpg");
        when(storage.saveImage(second, "listings")).thenReturn("listings/b.jpg");
        when(listings.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        ListingForm form = validForm();
        form.setTitle("  Green jacket  ");
        form.setBrand("   ");
        Listing saved = service.create(form, List.of(first, second), OWNER_ID);

        assertThat(saved.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(saved.getSeller()).isSameAs(owner);
        assertThat(saved.getCategory()).isSameAs(leaf);
        assertThat(saved.getTitle()).isEqualTo("Green jacket");
        assertThat(saved.getBrand()).isNull();
        assertThat(saved.getPhotos()).extracting(ListingPhoto::getFilePath).containsExactly("listings/a.jpg", "listings/b.jpg");
        assertThat(saved.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1);
        assertThat(saved.getFirstPhotoUrl()).isEqualTo("/uploads/listings/a.jpg");
    }

    @Test
    void publishRemovesAlreadyStoredFilesWhenALaterPhotoIsRejected() {
        MultipartFile good = photo("a.jpg");
        MultipartFile bad = photo("b.txt");
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(storage.saveImage(good, "listings")).thenReturn("listings/a.jpg");
        when(storage.saveImage(bad, "listings")).thenThrow(new BusinessRuleException("Only JPEG, PNG and WebP images are allowed."));

        assertThatThrownBy(() -> service.create(validForm(), List.of(good, bad), OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(storage).delete("listings/a.jpg");
        verify(listings, never()).save(any(Listing.class));
    }

    @Test
    void publishRefusesACategoryThatHasSubcategories() {
        Category parent = new Category();
        ReflectionTestUtils.setField(parent, "id", 1L);
        parent.getChildren().add(leaf);
        when(categories.findById(1L)).thenReturn(Optional.of(parent));
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        ListingForm form = validForm();
        form.setCategoryId(1L);

        assertThatThrownBy(() -> service.create(form, photos(1), OWNER_ID)).isInstanceOf(BusinessRuleException.class);
        verify(storage, never()).saveImage(any(), any());
    }

    @Test
    void bannedMembersCannotPublish() {
        User banned = user(OWNER_ID);
        when(banned.isBanned()).thenReturn(true);
        when(users.findById(OWNER_ID)).thenReturn(Optional.of(banned));
        assertThatThrownBy(() -> service.create(validForm(), photos(1), OWNER_ID)).isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------ edit and photo management

    @Test
    void editAppliesTheFormAndKeepsTheStatus() {
        Listing l = stored(ListingStatus.HIDDEN);
        l.addPhoto("listings/a.jpg");
        ListingForm form = validForm();
        form.setPrice(new BigDecimal("19.50"));
        form.setCity("Vilnius");

        service.update(LISTING_ID, form, List.of(), OWNER_ID, false);

        assertThat(l.getPrice()).isEqualByComparingTo("19.50");
        assertThat(l.getCity()).isEqualTo("Vilnius");
        assertThat(l.getStatus()).isEqualTo(ListingStatus.HIDDEN);
    }

    @Test
    void editRefusesMoreThanEightPhotosInTotal() {
        Listing l = stored(ListingStatus.ACTIVE);
        for (int i = 0; i < 7; i++) {
            l.addPhoto("listings/p" + i + ".jpg");
        }
        assertThatThrownBy(() -> service.update(LISTING_ID, validForm(), photos(2), OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(l.getPhotos()).hasSize(7);

        when(storage.saveImage(any(MultipartFile.class), eq("listings"))).thenReturn("listings/new.jpg");
        service.update(LISTING_ID, validForm(), photos(1), OWNER_ID, false);
        assertThat(l.getPhotos()).hasSize(8);
        assertThat(l.getPhotos().get(7).getSortIndex()).isEqualTo(7);
    }

    @Test
    void aSoldListingCannotBeEdited() {
        stored(ListingStatus.SOLD);
        assertThatThrownBy(() -> service.update(LISTING_ID, validForm(), List.of(), OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.getForEdit(LISTING_ID, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anotherMemberCannotEdit() {
        stored(ListingStatus.ACTIVE);
        assertThatThrownBy(() -> service.update(LISTING_ID, validForm(), List.of(), OTHER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.getForEdit(LISTING_ID, OTHER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void removingAPhotoClosesTheGapAndDeletesTheFile() {
        Listing l = storedWithPhotos(ListingStatus.ACTIVE, 3);
        service.removePhoto(LISTING_ID, 101L, OWNER_ID, false);

        assertThat(l.getPhotos()).extracting(ListingPhoto::getId).containsExactly(100L, 102L);
        assertThat(l.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1);
        verify(storage).delete("listings/p1.jpg");
    }

    @Test
    void theLastPhotoCannotBeRemoved() {
        Listing l = storedWithPhotos(ListingStatus.ACTIVE, 1);
        assertThatThrownBy(() -> service.removePhoto(LISTING_ID, 100L, OWNER_ID, false))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(l.getPhotos()).hasSize(1);
        verify(storage, never()).delete(any());
    }

    @Test
    void removingAnUnknownPhotoIsNotFound() {
        storedWithPhotos(ListingStatus.ACTIVE, 2);
        assertThatThrownBy(() -> service.removePhoto(LISTING_ID, 555L, OWNER_ID, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void makeCoverMovesThePhotoToPositionZeroAndKeepsTheOthersInOrder() {
        Listing l = storedWithPhotos(ListingStatus.ACTIVE, 4);
        service.makeCover(LISTING_ID, 102L, OWNER_ID, false);

        assertThat(l.getPhotos()).extracting(ListingPhoto::getId).containsExactly(102L, 100L, 101L, 103L);
        assertThat(l.getPhotos()).extracting(ListingPhoto::getSortIndex).containsExactly(0, 1, 2, 3);
        assertThat(l.getFirstPhotoPath()).isEqualTo("listings/p2.jpg");
    }

    @Test
    void makeCoverOnTheCoverChangesNothingAndUnknownIdIsNotFound() {
        Listing l = storedWithPhotos(ListingStatus.ACTIVE, 3);
        service.makeCover(LISTING_ID, 100L, OWNER_ID, false);
        assertThat(l.getPhotos()).extracting(ListingPhoto::getId).containsExactly(100L, 101L, 102L);

        assertThatThrownBy(() -> service.makeCover(LISTING_ID, 999L, OWNER_ID, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyTheOwnerOrAnAdminManagesPhotos() {
        storedWithPhotos(ListingStatus.ACTIVE, 3);
        assertThatThrownBy(() -> service.makeCover(LISTING_ID, 101L, OTHER_ID, false)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.removePhoto(LISTING_ID, 101L, OTHER_ID, false)).isInstanceOf(BusinessRuleException.class);
        service.makeCover(LISTING_ID, 101L, OTHER_ID, true);
    }

    // ------------------------------------------------------------ detail page and view counter

    @Test
    void aVisitorCountsAsAViewButTheOwnerDoesNot() {
        Listing l = stored(ListingStatus.ACTIVE);
        ReflectionTestUtils.setField(l, "viewCount", 4);

        ListingDetail asVisitor = service.getDetail(LISTING_ID, OTHER_ID, false);
        assertThat(asVisitor.getViews()).isEqualTo(5);
        assertThat(asVisitor.isOwner()).isFalse();
        assertThat(asVisitor.isCanManage()).isFalse();
        verify(listings).incrementViewCount(LISTING_ID);

        ListingDetail asOwner = service.getDetail(LISTING_ID, OWNER_ID, false);
        assertThat(asOwner.getViews()).isEqualTo(4);
        assertThat(asOwner.isCanManage()).isTrue();
        verify(listings).incrementViewCount(LISTING_ID); // still exactly once in total
    }

    @Test
    void hiddenListingsAreOnlyVisibleToTheOwnerAndAdmins() {
        stored(ListingStatus.HIDDEN);
        assertThatThrownBy(() -> service.getDetail(LISTING_ID, OTHER_ID, false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getDetail(LISTING_ID, null, false)).isInstanceOf(NotFoundException.class);
        assertThat(service.getDetail(LISTING_ID, OWNER_ID, false).getListing()).isNotNull();
        assertThat(service.getDetail(LISTING_ID, OTHER_ID, true).isCanManage()).isTrue();
        verify(listings, never()).incrementViewCount(any());
    }

    @Test
    void removedListingsAreOnlyVisibleToAdmins() {
        stored(ListingStatus.REMOVED);
        assertThatThrownBy(() -> service.getDetail(LISTING_ID, OWNER_ID, false)).isInstanceOf(NotFoundException.class);
        assertThat(service.getDetail(LISTING_ID, 1L, true).getListing()).isNotNull();
    }

    @Test
    void detailBuildsTheCategoryBreadcrumbFromTopLevelDown() {
        Listing l = stored(ListingStatus.ACTIVE);
        Category top = new Category();
        ReflectionTestUtils.setField(top, "id", 1L);
        top.setName("Clothing and shoes");
        top.setSlug("clothing-shoes");
        leaf.setParent(top);
        l.setCategory(leaf);

        ListingDetail detail = service.getDetail(LISTING_ID, OTHER_ID, false);
        assertThat(detail.getBreadcrumb()).hasSize(2);
        assertThat(detail.getBreadcrumb().get(0)).containsExactly("Clothing and shoes", "clothing-shoes");
        assertThat(detail.getBreadcrumb().get(1)).containsExactly("Shoes", "shoes");
    }

    // ------------------------------------------------------------ LIKE patterns

    @Test
    void likePatternEscapesWildcardsAndLowercases() {
        assertThat(ListingService.likePattern(null)).isEqualTo("%");
        assertThat(ListingService.likePattern("   ")).isEqualTo("%");
        assertThat(ListingService.likePattern(" Nike ")).isEqualTo("%nike%");
        assertThat(ListingService.likePattern("50%_off!")).isEqualTo("%50!%!_off!!%");
    }

    // ------------------------------------------------------------ helpers

    private User user(long id) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    /** A listing owned by OWNER_ID, in the given status, returned by the repository. */
    private Listing stored(ListingStatus status) {
        Listing l = new Listing();
        ReflectionTestUtils.setField(l, "id", LISTING_ID);
        l.setSeller(owner);
        l.setCategory(leaf);
        l.setTitle("Green jacket");
        l.setDescription("A warm green jacket in good condition.");
        l.setPrice(new BigDecimal("30.00"));
        l.setCondition(ItemCondition.GOOD);
        l.setCity("Kaunas");
        l.setStatus(status);
        when(listings.findDetailById(LISTING_ID)).thenReturn(Optional.of(l));
        return l;
    }

    /** Like {@link #stored} with n photos, ids 100, 101, ... and paths listings/p0.jpg, listings/p1.jpg, ... */
    private Listing storedWithPhotos(ListingStatus status, int n) {
        Listing l = stored(status);
        for (int i = 0; i < n; i++) {
            ListingPhoto p = l.addPhoto("listings/p" + i + ".jpg");
            p.setId(100L + i);
        }
        return l;
    }

    private ListingForm validForm() {
        ListingForm form = new ListingForm();
        form.setTitle("Green jacket");
        form.setDescription("A warm green jacket in good condition.");
        form.setPrice(new BigDecimal("30.00"));
        form.setCondition(ItemCondition.GOOD);
        form.setCategoryId(11L);
        form.setCity("Kaunas");
        return form;
    }

    private MultipartFile photo(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[] {1, 2, 3});
    }

    private List<MultipartFile> photos(int n) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            files.add(photo("p" + i + ".jpg"));
        }
        return files;
    }
}
