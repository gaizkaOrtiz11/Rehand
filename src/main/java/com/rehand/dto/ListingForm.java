package com.rehand.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.rehand.domain.Listing;
import com.rehand.domain.ItemCondition;

/**
 * Create and edit form of a listing. The photos are not part of this object: they arrive as a
 * separate multipart parameter and are checked by {@code ListingService} (1 to 8 photos).
 */
public class ListingForm {

    @NotBlank(message = "Enter a title.")
    @Size(min = 3, max = 120, message = "The title must be 3 to 120 characters long.")
    private String title;

    @NotBlank(message = "Describe the item.")
    @Size(min = 10, max = 4000, message = "The description must be 10 to 4000 characters long.")
    private String description;

    @NotNull(message = "Enter a price.")
    @DecimalMin(value = "0.01", message = "The price must be at least 0.01.")
    @DecimalMax(value = "99999.99", message = "The price can be at most 99999.99.")
    @Digits(integer = 5, fraction = 2, message = "Use at most two decimals.")
    private BigDecimal price;

    @NotNull(message = "Choose the condition.")
    private ItemCondition condition;

    @NotNull(message = "Choose a category.")
    private Long categoryId;

    @Size(max = 60, message = "The brand can have at most 60 characters.")
    private String brand;

    @Size(max = 20, message = "The size can have at most 20 characters.")
    private String size;

    @Size(max = 30, message = "The color can have at most 30 characters.")
    private String color;

    @NotBlank(message = "Enter the city where the item is.")
    @Size(max = 80, message = "The city can have at most 80 characters.")
    private String city;

    public ListingForm() {
        // empty form for the "new listing" page
    }

    /** Pre-fills the edit form from an existing listing (category must be loaded). */
    public static ListingForm from(Listing listing) {
        ListingForm form = new ListingForm();
        form.title = listing.getTitle();
        form.description = listing.getDescription();
        form.price = listing.getPrice();
        form.condition = listing.getCondition();
        form.categoryId = listing.getCategory().getId();
        form.brand = listing.getBrand();
        form.size = listing.getItemSize();
        form.color = listing.getColor();
        form.city = listing.getCity();
        return form;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
