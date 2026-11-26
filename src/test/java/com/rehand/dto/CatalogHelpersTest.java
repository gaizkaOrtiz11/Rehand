package com.rehand.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rehand.exception.NotFoundException;

/** Small pure helpers: pagination window, initials, category tree helpers. */
class CatalogHelpersTest {

    @Test
    void paginationWindowShowsFirstLastAndNeighboursWithGaps() {
        assertThat(PageNav.window(1, 1)).isEmpty();
        assertThat(PageNav.window(1, 0)).isEmpty();
        assertThat(PageNav.window(2, 4)).containsExactly(1, 2, 3, 4);
        assertThat(PageNav.window(1, 10)).containsExactly(1, 2, 3, 0, 10);
        assertThat(PageNav.window(5, 10)).containsExactly(1, 0, 3, 4, 5, 6, 7, 0, 10);
        assertThat(PageNav.window(10, 10)).containsExactly(1, 0, 8, 9, 10);
        assertThat(PageNav.window(4, 10)).containsExactly(1, 2, 3, 4, 5, 6, 0, 10);
    }

    @Test
    void initialsComeFromTheWordsOfTheUsername() {
        assertThat(Initials.of("ausra_k")).isEqualTo("AK");
        assertThat(Initials.of("mikel_b")).isEqualTo("MB");
        assertThat(Initials.of("admin")).isEqualTo("AD");
        assertThat(Initials.of("x")).isEqualTo("X");
        assertThat(Initials.of("  ")).isEqualTo("?");
        assertThat(Initials.of(null)).isEqualTo("?");
        assertThat(Initials.of("jonas petrauskas")).isEqualTo("JP");
    }

    private static List<CategoryNode> tree() {
        CategoryNode clothing = new CategoryNode(1L, "Clothing and shoes", "clothing-shoes", null);
        CategoryNode shoes = new CategoryNode(13L, "Shoes", "shoes", clothing);
        CategoryNode womens = new CategoryNode(11L, "Women's clothing", "womens-clothing", clothing);
        clothing.getChildren().add(womens);
        clothing.getChildren().add(shoes);
        CategoryNode books = new CategoryNode(4L, "Books", "books-music-film", null);
        return List.of(clothing, books);
    }

    @Test
    void aCategoryFilterMatchesTheCategoryAndItsDescendants() {
        List<CategoryNode> roots = tree();
        assertThat(roots.get(0).collectIds()).containsExactly(1L, 11L, 13L);
        assertThat(roots.get(1).collectIds()).containsExactly(4L);
        assertThat(CategoryNode.findBySlug(roots, "shoes").getId()).isEqualTo(13L);
        assertThat(CategoryNode.findBySlug(roots, "nope")).isNull();
        assertThat(roots.get(0).getChildNames()).isEqualTo("Women's clothing, Shoes");
    }

    @Test
    void contextOffersChildrenOfAParentAndSiblingsOfALeaf() {
        List<CategoryNode> roots = tree();

        CategoryContext none = CategoryContext.of(roots, null);
        assertThat(none.getSelected()).isNull();
        assertThat(none.getRail()).isEqualTo(roots);
        assertThat(none.getCategoryIds()).isNull();

        CategoryContext top = CategoryContext.of(roots, "clothing-shoes");
        assertThat(top.getRail()).extracting(CategoryNode::getSlug).containsExactly("womens-clothing", "shoes");
        assertThat(top.getCategoryIds()).containsExactly(1L, 11L, 13L);

        CategoryContext leaf = CategoryContext.of(roots, "shoes");
        assertThat(leaf.getBreadcrumb()).extracting(CategoryNode::getName).containsExactly("Clothing and shoes", "Shoes");
        assertThat(leaf.getRail()).extracting(CategoryNode::getSlug).containsExactly("womens-clothing", "shoes");
        assertThat(leaf.getCategoryIds()).containsExactly(13L);
    }

    @Test
    void anUnknownCategorySlugIsNotFound() {
        assertThatThrownBy(() -> CategoryContext.of(tree(), "nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void groupsForTheFormOfferLeavesAndGroupsForTheFilterOfferEverything() {
        List<CategoryNode> roots = tree();

        List<CategoryGroup> form = CategoryGroup.forListingForm(roots);
        assertThat(form).extracting(CategoryGroup::getLabel).containsExactly("Clothing and shoes", "Books");
        assertThat(form.get(0).getOptions()).extracting(CategoryGroup.Option::getLabel)
                .containsExactly("Women's clothing", "Shoes");
        // a top-level category without children is selectable itself
        assertThat(form.get(1).getOptions()).extracting(CategoryGroup.Option::getId).containsExactly(4L);

        List<CategoryGroup> filter = CategoryGroup.forFilter(roots);
        assertThat(filter.get(0).getOptions()).extracting(CategoryGroup.Option::getLabel)
                .containsExactly("All in Clothing and shoes", "Women's clothing", "Shoes");
        assertThat(filter.get(0).getOptions().get(0).getSlug()).isEqualTo("clothing-shoes");
    }
}
