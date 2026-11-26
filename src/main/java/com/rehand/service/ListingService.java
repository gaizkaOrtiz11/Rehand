package com.rehand.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.rehand.domain.Category;
import com.rehand.domain.ItemCondition;
import com.rehand.domain.Listing;
import com.rehand.domain.ListingPhoto;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.User;
import com.rehand.dto.ListingDetail;
import com.rehand.dto.ListingForm;
import com.rehand.dto.ListingSearchCriteria;
import com.rehand.dto.SellerPageView;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.CategoryRepository;
import com.rehand.repository.ListingRepository;
import com.rehand.repository.UserRepository;

/**
 * Business rules of the catalog: search, detail page, publishing, editing, photos and the
 * manual status changes (hide / unhide / delete). The caller passes the acting user as
 * {@code actorId} (null for guests) and {@code admin}. Admins may change any listing,
 * members only their own; everything else is a {@link BusinessRuleException}.
 *
 * Methods that return entities initialise what the templates read (photos, seller, category)
 * inside the transaction, because the app runs with open-in-view switched off.
 */
@Service
@Transactional(readOnly = true)
public class ListingService {

    private static final List<ListingStatus> AVAILABLE_ONLY = List.of(ListingStatus.ACTIVE);
    private static final List<ListingStatus> PUBLIC_STATUSES =
            List.of(ListingStatus.ACTIVE, ListingStatus.RESERVED, ListingStatus.SOLD);
    private static final List<ListingStatus> OWNER_STATUSES =
            List.of(ListingStatus.ACTIVE, ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.HIDDEN);

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorage;
    private final OfferService offerService;

    public ListingService(ListingRepository listingRepository,
                          CategoryRepository categoryRepository,
                          UserRepository userRepository,
                          FileStorageService fileStorage,
                          OfferService offerService) {
        this.listingRepository = listingRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.offerService = offerService;
    }

    // ================================================================ browse and search

    /**
     * Public search. Only ACTIVE listings unless {@code includeUnavailable} is set, then also RESERVED and SOLD.
     * HIDDEN and REMOVED listings never appear.
     *
     * @param categoryIds ids to match (a category and its descendants), or null/empty for all categories
     */
    public Page<Listing> search(ListingSearchCriteria c, Collection<Long> categoryIds) {
        boolean allCategories = categoryIds == null || categoryIds.isEmpty();
        Collection<Long> ids = allCategories ? List.of(-1L) : categoryIds;
        boolean allConditions = c.getConditions().isEmpty();
        List<ItemCondition> conditions = allConditions ? List.of(ItemCondition.NEW) : c.getConditions();
        List<String> terms = c.getTerms();
        Pageable pageable = PageRequest.of(c.getPageIndex(), ListingSearchCriteria.PAGE_SIZE, c.toSort());

        Page<Listing> page = listingRepository.search(
                c.isIncludeUnavailable() ? PUBLIC_STATUSES : AVAILABLE_ONLY,
                c.getEffectiveMin(), c.getEffectiveMax(),
                allCategories, ids,
                allConditions, conditions,
                likePattern(c.getCity()), likePattern(c.getSize()), likePattern(c.getColor()), likePattern(c.getBrand()),
                likePattern(term(terms, 0)), likePattern(term(terms, 1)), likePattern(term(terms, 2)),
                pageable);
        initPhotos(page.getContent());
        return page;
    }

    /** City names for the datalist of the filter form. */
    public List<String> cities() {
        return listingRepository.findCities(PUBLIC_STATUSES);
    }

    /** The newest ACTIVE listings for the home page. */
    public List<Listing> latestActive(int count) {
        Pageable pageable = PageRequest.of(0, count,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<Listing> list = listingRepository.findByStatusIn(AVAILABLE_ONLY, pageable).getContent();
        initPhotos(list);
        return list;
    }

    /** Up to six ACTIVE listings whose title contains the text (at least two characters). */
    public List<Listing> suggest(String text) {
        if (text == null) {
            return List.of();
        }
        String t = text.trim();
        if (t.length() < 2) {
            return List.of();
        }
        if (t.length() > 60) {
            t = t.substring(0, 60);
        }
        return listingRepository.findTop6ByStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(ListingStatus.ACTIVE, t);
    }

    // ================================================================ detail

    /**
     * Loads the detail page data and counts the view (once per call, not for the owner, and only
     * for publicly visible listings). HIDDEN listings are visible to the owner and admins,
     * REMOVED ones to admins only; everyone else gets a NotFoundException.
     */
    @Transactional
    public ListingDetail getDetail(Long id, Long actorId, boolean admin) {
        Listing listing = listingRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + id));
        boolean owner = listing.isOwnedBy(actorId);
        boolean canManage = owner || admin;
        ListingStatus status = listing.getStatus();
        if (status == ListingStatus.REMOVED && !admin) {
            throw new NotFoundException("Listing not found: " + id);
        }
        if (status == ListingStatus.HIDDEN && !canManage) {
            throw new NotFoundException("Listing not found: " + id);
        }

        int views = listing.getViewCount();
        boolean publicStatus = PUBLIC_STATUSES.contains(status);
        if (!owner && publicStatus) {
            listingRepository.incrementViewCount(id);
            views++;
        }

        listing.getPhotos().size();
        List<String[]> crumbs = new ArrayList<>();
        for (Category c = listing.getCategory(); c != null; c = c.getParent()) {
            crumbs.add(0, new String[] {c.getName(), c.getSlug()});
        }

        List<Listing> related = new ArrayList<>();
        for (Listing other : listingRepository.findTop5ByCategoryIdAndStatusOrderByCreatedAtDesc(
                listing.getCategory().getId(), ListingStatus.ACTIVE)) {
            if (!other.getId().equals(id) && related.size() < 4) {
                related.add(other);
            }
        }
        initPhotos(related);

        long sellerActive = listingRepository.countBySellerIdAndStatus(listing.getSeller().getId(), ListingStatus.ACTIVE);
        return new ListingDetail(listing, crumbs, related, sellerActive, owner, canManage, views);
    }

    // ================================================================ publish and edit

    /**
     * Publishes a listing with 1 to 8 photos.
     *
     * @throws BusinessRuleException no photo, more than 8 photos, bad image, unknown or non-leaf category
     */
    @Transactional
    public Listing create(ListingForm form, List<MultipartFile> photos, Long sellerId) {
        List<MultipartFile> files = nonEmpty(photos);
        if (files.isEmpty()) {
            throw new BusinessRuleException("Add at least one photo.");
        }
        if (files.size() > Listing.MAX_PHOTOS) {
            throw new BusinessRuleException("A listing can have at most " + Listing.MAX_PHOTOS + " photos.");
        }
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new NotFoundException("User not found: " + sellerId));
        if (seller.isBanned()) {
            throw new BusinessRuleException("This account cannot publish listings.");
        }
        Category category = requireCategory(form.getCategoryId());

        Listing listing = new Listing();
        listing.setSeller(seller);
        apply(form, listing, category);
        listing.setStatus(ListingStatus.ACTIVE);
        List<String> stored = storePhotos(files, listing);
        try {
            return listingRepository.save(listing);
        } catch (RuntimeException e) {
            deleteFiles(stored);
            throw e;
        }
    }

    /** The listing for the edit form, with photos loaded. Only the owner or an admin; not SOLD or REMOVED. */
    public Listing getForEdit(Long id, Long actorId, boolean admin) {
        Listing listing = loadManageable(id, actorId, admin);
        if (!listing.isEditable()) {
            throw new BusinessRuleException("This listing can no longer be edited.");
        }
        listing.getPhotos().size();
        return listing;
    }

    /** Saves the edit form and appends new photos (existing photos plus new ones must stay at 8 or fewer). */
    @Transactional
    public Listing update(Long id, ListingForm form, List<MultipartFile> newPhotos, Long actorId, boolean admin) {
        Listing listing = loadManageable(id, actorId, admin);
        if (!listing.isEditable()) {
            throw new BusinessRuleException("This listing can no longer be edited.");
        }
        List<MultipartFile> files = nonEmpty(newPhotos);
        if (listing.getPhotos().size() + files.size() > Listing.MAX_PHOTOS) {
            throw new BusinessRuleException("A listing can have at most " + Listing.MAX_PHOTOS + " photos. "
                    + "Remove a photo first or choose fewer new ones.");
        }
        Category category = requireCategory(form.getCategoryId());
        apply(form, listing, category);
        storePhotos(files, listing);
        return listing;
    }

    // ================================================================ status, delete, photos

    /**
     * Manual status change: only ACTIVE to HIDDEN (hide) and HIDDEN to ACTIVE (unhide).
     * RESERVED and SOLD are set by the offer and order steps. Hiding declines the open offers.
     * The listing row is locked first, so an offer cannot be accepted at the same moment.
     */
    @Transactional
    public Listing changeStatus(Long id, ListingStatus target, Long actorId, boolean admin) {
        listingRepository.findByIdForUpdate(id);
        Listing listing = loadManageable(id, actorId, admin);
        if (target == ListingStatus.HIDDEN) {
            listing.hide();
            offerService.closeOpenOffers(listing, null, "the seller took the listing off sale.");
        } else if (target == ListingStatus.ACTIVE) {
            listing.unhide();
        } else {
            throw new BusinessRuleException("A listing can only be hidden or made available again here.");
        }
        return listing;
    }

    /** Soft delete: the status becomes REMOVED and open offers are declined. Sold and reserved listings cannot be deleted. */
    @Transactional
    public Listing delete(Long id, Long actorId, boolean admin) {
        listingRepository.findByIdForUpdate(id);
        Listing listing = loadManageable(id, actorId, admin);
        listing.markRemoved();
        offerService.closeOpenOffers(listing, null, "the listing was deleted.");
        return listing;
    }

    /** Removes one photo; the last remaining photo cannot be removed. */
    @Transactional
    public void removePhoto(Long listingId, Long photoId, Long actorId, boolean admin) {
        Listing listing = loadManageable(listingId, actorId, admin);
        if (!listing.isEditable()) {
            throw new BusinessRuleException("This listing can no longer be edited.");
        }
        if (listing.getPhotos().size() <= 1) {
            throw new BusinessRuleException("A listing needs at least one photo. Add another one before removing this one.");
        }
        ListingPhoto removed = listing.removePhoto(photoId);
        if (removed == null) {
            throw new NotFoundException("Photo not found: " + photoId);
        }
        String path = removed.getFilePath();
        afterCommit(() -> deleteFiles(List.of(path)));
    }

    /** Makes a photo the cover (position 0); the others keep their order. */
    @Transactional
    public void makeCover(Long listingId, Long photoId, Long actorId, boolean admin) {
        Listing listing = loadManageable(listingId, actorId, admin);
        if (!listing.isEditable()) {
            throw new BusinessRuleException("This listing can no longer be edited.");
        }
        listing.getPhotos().size();
        if (!listing.makeCover(photoId)) {
            throw new NotFoundException("Photo not found: " + photoId);
        }
    }

    // ================================================================ seller pages

    /** Public seller page: the member and one page of their ACTIVE listings. */
    public SellerPageView sellerPage(Long userId, int pageIndex) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        if (seller.isBanned()) {
            throw new NotFoundException("User not found: " + userId);
        }
        Pageable pageable = PageRequest.of(Math.max(pageIndex, 0), ListingSearchCriteria.PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Listing> page = listingRepository.findBySellerIdAndStatusIn(userId, AVAILABLE_ONLY, pageable);
        initPhotos(page.getContent());
        long active = listingRepository.countBySellerIdAndStatus(userId, ListingStatus.ACTIVE);
        return new SellerPageView(seller, page, active);
    }

    /**
     * The member's own listings in every status except REMOVED, newest first.
     *
     * @param status only this status, or null for all
     */
    public Page<Listing> myListings(Long userId, ListingStatus status, int pageIndex) {
        Collection<ListingStatus> statuses = status == null || status == ListingStatus.REMOVED
                ? OWNER_STATUSES : List.of(status);
        Pageable pageable = PageRequest.of(Math.max(pageIndex, 0), ListingSearchCriteria.PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Listing> page = listingRepository.findBySellerIdAndStatusIn(userId, statuses, pageable);
        initPhotos(page.getContent());
        return page;
    }

    // ================================================================ helpers

    /** Loads a listing for a change and checks who is asking. REMOVED listings do not exist for this purpose. */
    private Listing loadManageable(Long id, Long actorId, boolean admin) {
        Listing listing = listingRepository.findDetailById(id)
                .filter(l -> l.getStatus() != ListingStatus.REMOVED)
                .orElseThrow(() -> new NotFoundException("Listing not found: " + id));
        if (!admin && !listing.isOwnedBy(actorId)) {
            throw new BusinessRuleException("You can only change your own listings.");
        }
        return listing;
    }

    private Category requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessRuleException("Choose a category.");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Choose a category from the list."));
        if (!category.getChildren().isEmpty()) {
            throw new BusinessRuleException("Choose a more specific category.");
        }
        return category;
    }

    private void apply(ListingForm form, Listing listing, Category category) {
        listing.setTitle(form.getTitle().trim());
        listing.setDescription(form.getDescription().trim());
        listing.setPrice(form.getPrice());
        listing.setCondition(form.getCondition());
        listing.setCategory(category);
        listing.setBrand(blankToNull(form.getBrand()));
        listing.setItemSize(blankToNull(form.getSize()));
        listing.setColor(blankToNull(form.getColor()));
        listing.setCity(form.getCity().trim());
    }

    /** Stores the files and attaches them to the listing; on failure the files stored so far are deleted. */
    private List<String> storePhotos(List<MultipartFile> files, Listing listing) {
        List<String> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String path = fileStorage.saveImage(file, "listings");
                stored.add(path);
                listing.addPhoto(path);
            }
        } catch (RuntimeException e) {
            deleteFiles(stored);
            throw e;
        }
        return stored;
    }

    private void deleteFiles(List<String> paths) {
        for (String path : paths) {
            if (path == null || path.startsWith(ListingPhoto.DEMO_PREFIX)) {
                continue;
            }
            try {
                fileStorage.delete(path);
            } catch (RuntimeException ignored) {
                // a leftover file is harmless; never hide the original problem
            }
        }
    }

    /** Runs the action after the transaction commits, or right away when no transaction is active. */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Touches the cover photo so the lazy photo collections are loaded (batched) inside the transaction. */
    private void initPhotos(List<Listing> listings) {
        for (Listing l : listings) {
            l.getFirstPhotoPath();
        }
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        List<MultipartFile> out = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    private static String term(List<String> terms, int index) {
        return index < terms.size() ? terms.get(index) : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * SQL LIKE pattern "%text%" in lower case with % _ and the escape character "!" escaped;
     * "%" (matches everything) when the text is null or blank.
     */
    public static String likePattern(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String t = text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + t + "%";
    }
}
