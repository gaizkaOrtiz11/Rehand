package com.rehand.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * A top-level category with the selectable options below it, for {@code <optgroup>} lists.
 * {@link #forListingForm} offers only leaf categories (a listing belongs to the most specific one);
 * {@link #forFilter} offers every descendant plus an "All in ..." option for the top-level itself.
 */
public class CategoryGroup {

    private final String label;
    private final List<Option> options = new ArrayList<>();

    private CategoryGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public List<Option> getOptions() {
        return options;
    }

    /** One {@code <option>}: id for the listing form, slug for the filter. */
    public static class Option {
        private final Long id;
        private final String slug;
        private final String label;

        public Option(Long id, String slug, String label) {
            this.id = id;
            this.slug = slug;
            this.label = label;
        }

        public Long getId() {
            return id;
        }

        public String getSlug() {
            return slug;
        }

        public String getLabel() {
            return label;
        }
    }

    public static List<CategoryGroup> forListingForm(List<CategoryNode> roots) {
        List<CategoryGroup> groups = new ArrayList<>();
        for (CategoryNode root : roots) {
            CategoryGroup group = new CategoryGroup(root.getName());
            if (root.isLeaf()) {
                group.options.add(new Option(root.getId(), root.getSlug(), root.getName()));
            } else {
                for (CategoryNode child : root.getChildren()) {
                    addLeaves(group, child, "");
                }
            }
            groups.add(group);
        }
        return groups;
    }

    public static List<CategoryGroup> forFilter(List<CategoryNode> roots) {
        List<CategoryGroup> groups = new ArrayList<>();
        for (CategoryNode root : roots) {
            CategoryGroup group = new CategoryGroup(root.getName());
            group.options.add(new Option(root.getId(), root.getSlug(), "All in " + root.getName()));
            for (CategoryNode child : root.getChildren()) {
                addAll(group, child, "");
            }
            groups.add(group);
        }
        return groups;
    }

    private static void addLeaves(CategoryGroup group, CategoryNode node, String prefix) {
        String label = prefix + node.getName();
        if (node.isLeaf()) {
            group.options.add(new Option(node.getId(), node.getSlug(), label));
            return;
        }
        for (CategoryNode child : node.getChildren()) {
            addLeaves(group, child, label + " / ");
        }
    }

    private static void addAll(CategoryGroup group, CategoryNode node, String prefix) {
        String label = prefix + node.getName();
        group.options.add(new Option(node.getId(), node.getSlug(), label));
        for (CategoryNode child : node.getChildren()) {
            addAll(group, child, label + " / ");
        }
    }
}
