package com.poly.graduation_project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.poly.graduation_project.model.Image;
import com.poly.graduation_project.model.Product;

import jakarta.transaction.Transactional;

public interface ImageRepository extends JpaRepository<Image, Integer> {
// Lấy tất cả ảnh gallery của 1 sản phẩm
    List<Image> findByProduct(Product product);

    // Xóa toàn bộ ảnh gallery của 1 sản phẩm (dùng khi xóa sản phẩm)
    @Transactional
    @Modifying
    @Query("DELETE FROM Image i WHERE i.product.id = :productId")
    void deleteByProductId(Integer productId);
}
