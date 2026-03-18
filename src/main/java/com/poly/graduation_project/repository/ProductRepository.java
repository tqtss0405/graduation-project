package com.poly.graduation_project.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

  // Lấy sản phẩm theo danh mục và đang bán
  List<Product> findByCategoryAndActiveTrue(Category category);

  // Lấy tất cả sản phẩm đang bán nằm trong khoảng giá (cũ - không dùng phân
  // trang)
  List<Product> findByActiveTrueAndPriceBetween(Double finalMinPrice, Double finalMaxPrice);

  // Lấy sản phẩm đang bán, THEO DANH MỤC và nằm trong khoảng giá (cũ - không dùng
  // phân trang)
  List<Product> findByCategoryAndActiveTrueAndPriceBetween(Category category, Double finalMinPrice,
      Double finalMaxPrice);

  // Có Sort (cũ - giữ lại nếu dùng ở chỗ khác)
  List<Product> findByActiveTrueAndPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Sort sort);

  List<Product> findByCategoryAndActiveTrueAndPriceBetween(Category category, BigDecimal minPrice, BigDecimal maxPrice,
      Sort sort);

  // ✅ MỚI: Có Pageable (hỗ trợ phân trang + sắp xếp)
  Page<Product> findByActiveTrueAndPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

  Page<Product> findByCategoryAndActiveTrueAndPriceBetween(Category category, BigDecimal minPrice, BigDecimal maxPrice,
      Pageable pageable);

}