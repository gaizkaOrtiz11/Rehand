package com.rehand.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rehand.domain.Category;
import com.rehand.domain.ListingStatus;
import com.rehand.dto.CategoryNode;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.CategoryRepository;
import com.rehand.repository.ListingRepository;

/**
 * Reads the category tree. The result is a tree of plain {@link CategoryNode} objects that
 * carry the number of ACTIVE listings (a parent counts its children too), so templates and
 * controllers never touch lazy JPA associations.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final long ROOT_KEY = 0L;

    private final CategoryRepository categoryRepository;
    private final ListingRepository listingRepository;

    public CategoryService(CategoryRepository categoryRepository, ListingRepository listingRepository) {
        this.categoryRepository = categoryRepository;
        this.listingRepository = listingRepository;
    }

    /** The top-level categories with their children, in sort order, with listing counts. */
    public List<CategoryNode> tree() {
        List<Category> all = categoryRepository.findAll(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name")));
        Map<Long, Long> ownCounts = new HashMap<>();
        for (Object[] row : listingRepository.countByCategory(ListingStatus.ACTIVE)) {
            ownCounts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            long key = c.getParent() == null ? ROOT_KEY : c.getParent().getId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        return build(null, ROOT_KEY, byParent, ownCounts);
    }

    private List<CategoryNode> build(CategoryNode parent, long parentKey,
                                     Map<Long, List<Category>> byParent, Map<Long, Long> ownCounts) {
        List<CategoryNode> nodes = new ArrayList<>();
        List<Category> level = byParent.get(parentKey);
        if (level == null) {
            return nodes;
        }
        for (Category c : level) {
            CategoryNode node = new CategoryNode(c.getId(), c.getName(), c.getSlug(), parent);
            node.getChildren().addAll(build(node, c.getId(), byParent, ownCounts));
            long total = ownCounts.getOrDefault(c.getId(), 0L);
            for (CategoryNode child : node.getChildren()) {
                total += child.getCount();
            }
            node.setCount(total);
            nodes.add(node);
        }
        return nodes;
    }

    /** Checks that a slug exists (for {@code /categories/{slug}}). */
    public Category requireBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Category not found: " + slug));
    }
}
