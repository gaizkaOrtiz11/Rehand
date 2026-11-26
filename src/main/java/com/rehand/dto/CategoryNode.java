package com.rehand.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of a category for templates: name, slug, number of ACTIVE listings
 * (including the sub-categories) and the child nodes. A plain class with getters,
 * so it needs no open session.
 */
public class CategoryNode {

    private final Long id;
    private final String name;
    private final String slug;
    private final CategoryNode parent;
    private final List<CategoryNode> children = new ArrayList<>();
    private long count;

    public CategoryNode(Long id, String name, String slug, CategoryNode parent) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public CategoryNode getParent() {
        return parent;
    }

    public List<CategoryNode> getChildren() {
        return children;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** Comma separated names of the children, for the short description on the home page tiles. */
    public String getChildNames() {
        StringBuilder sb = new StringBuilder();
        for (CategoryNode c : children) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.getName());
        }
        return sb.toString();
    }

    /** This node's id and the ids of all its descendants (what a category filter has to match). */
    public List<Long> collectIds() {
        List<Long> ids = new ArrayList<>();
        collectIds(this, ids);
        return ids;
    }

    private static void collectIds(CategoryNode node, List<Long> ids) {
        ids.add(node.id);
        for (CategoryNode child : node.children) {
            collectIds(child, ids);
        }
    }

    /** Finds a node by slug in a forest of top-level nodes, or returns null. */
    public static CategoryNode findBySlug(List<CategoryNode> roots, String slug) {
        if (slug == null) {
            return null;
        }
        for (CategoryNode root : roots) {
            CategoryNode hit = findBySlug(root, slug);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static CategoryNode findBySlug(CategoryNode node, String slug) {
        if (node.slug.equals(slug)) {
            return node;
        }
        for (CategoryNode child : node.children) {
            CategoryNode hit = findBySlug(child, slug);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }
}
