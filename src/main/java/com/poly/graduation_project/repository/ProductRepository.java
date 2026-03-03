package com.poly.graduation_project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Category;
import com.poly.graduation_project.model.Product;

public interface ProductRepository extends JpaRepository<Product, Integer> {
      // Lấy tất cả sản phẩm đang bán
    List<Product> findByActiveTrue();

    // Lấy 8 sản phẩm mới nhất đang bán (cho trang chủ)
    List<Product> findTop8ByActiveTrueOrderByIdDesc();

    // Lấy sản phẩm theo danh mục
    List<Product> findByCategory(Category category);

    // Tìm theo slug (cho trang chi tiết)
    Product findBySlug(String slug);

    // Lấy 4 sản phẩm cùng danh mục (gợi ý ở trang chi tiết)
    List<Product> findTop4ByCategoryAndIdNotAndActiveTrue(Category category, Integer id);
}
