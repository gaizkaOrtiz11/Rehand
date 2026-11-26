package com.rehand.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rehand.exception.NotFoundException;

/**
 * What the browse page needs to know about the selected category: the node, the breadcrumb
 * from the top level down, and the "rail" of categories to offer next (the children of the
 * selection, or its siblings when it has no children, or the top level when nothing is selected).
 */
public class CategoryContext {

    private final CategoryNode selected;
    private final List<CategoryNode> breadcrumb;
    private final List<CategoryNode> rail;

    private CategoryContext(CategoryNode selected, List<CategoryNode> breadcrumb, List<CategoryNode> rail) {
        this.selected = selected;
        this.breadcrumb = breadcrumb;
        this.rail = rail;
    }

    /**
     * @param roots top-level nodes
     * @param slug  selected slug or null
     * @throws NotFoundException when the slug is given but unknown
     */
    public static CategoryContext of(List<CategoryNode> roots, String slug) {
        if (slug == null || slug.isBlank()) {
            return new CategoryContext(null, Collections.emptyList(), roots);
        }
        CategoryNode node = CategoryNode.findBySlug(roots, slug);
        if (node == null) {
            throw new NotFoundException("Category not found: " + slug);
        }
        List<CategoryNode> trail = new ArrayList<>();
        for (CategoryNode n = node; n != null; n = n.getParent()) {
            trail.add(0, n);
        }
        List<CategoryNode> rail;
        if (!node.isLeaf()) {
            rail = node.getChildren();
        } else if (node.getParent() != null) {
            rail = node.getParent().getChildren();
        } else {
            rail = roots;
        }
        return new CategoryContext(node, trail, rail);
    }

    public CategoryNode getSelected() {
        return selected;
    }

    public List<CategoryNode> getBreadcrumb() {
        return breadcrumb;
    }

    public List<CategoryNode> getRail() {
        return rail;
    }

    /** Ids to filter by (selected category and descendants), or null when no category is selected. */
    public List<Long> getCategoryIds() {
        return selected == null ? null : selected.collectIds();
    }
}
