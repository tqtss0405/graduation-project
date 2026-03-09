package com.poly.graduation_project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;

import jakarta.transaction.Transactional;

public interface CartDetailRepository extends JpaRepository<CartDetail, Integer> {
    // Lấy toàn bộ giỏ hàng của user
    List<CartDetail> findByUser(User user);

    // Tìm item cụ thể trong giỏ (user + product)
    Optional<CartDetail> findByUserAndProduct(User user, Product product);

    // Đếm số lượng item trong giỏ
    @Query("SELECT COALESCE(SUM(c.quantity), 0) FROM CartDetail c WHERE c.user = :user")
    int countTotalQuantityByUser(@Param("user") User user);

    // Xóa toàn bộ giỏ hàng sau khi đặt hàng
    @Transactional
    void deleteByUser(User user);
}
