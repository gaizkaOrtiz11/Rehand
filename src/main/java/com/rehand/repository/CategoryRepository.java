package com.rehand.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rehand.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);
}
