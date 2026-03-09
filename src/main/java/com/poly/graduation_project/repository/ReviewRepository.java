package com.poly.graduation_project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.model.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    // Kiểm tra order_detail đã được review chưa (mỗi đơn chỉ review 1 lần)
    boolean existsByOrderDetail(OrderDetail orderDetail);

    // Lấy tất cả review của 1 sản phẩm (qua orderDetail -> product)
    @Query("SELECT r FROM Review r WHERE r.orderDetail.product.id = :productId ORDER BY r.createAt DESC")
    List<Review> findByProductId(@Param("productId") Integer productId);

    // Lấy tất cả review của 1 sản phẩm theo số sao
    @Query("SELECT r FROM Review r WHERE r.orderDetail.product.id = :productId AND r.rating = :rating ORDER BY r.createAt DESC")
    List<Review> findByProductIdAndRating(@Param("productId") Integer productId, @Param("rating") Integer rating);

    // Đếm số review theo sản phẩm
    @Query("SELECT COUNT(r) FROM Review r WHERE r.orderDetail.product.id = :productId")
    long countByProductId(@Param("productId") Integer productId);

    // Tính điểm trung bình theo sản phẩm
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.orderDetail.product.id = :productId")
    Double avgRatingByProductId(@Param("productId") Integer productId);

    // Lấy review của user cho 1 order_detail cụ thể
    Optional<Review> findByOrderDetail(OrderDetail orderDetail);

    // Lấy tất cả review sắp xếp mới nhất (cho admin)
    List<Review> findAllByOrderByCreateAtDesc();

    // Lọc theo số sao (cho admin)
    List<Review> findByRatingOrderByCreateAtDesc(Integer rating);

    // Tổng số review
    @Query("SELECT COUNT(r) FROM Review r")
    long countAll();

    // Điểm trung bình toàn hệ thống
    @Query("SELECT AVG(r.rating) FROM Review r")
    Double avgRatingAll();

    // Số review trong tháng hiện tại
    @Query("SELECT COUNT(r) FROM Review r WHERE MONTH(r.createAt) = MONTH(CURRENT_DATE) AND YEAR(r.createAt) = YEAR(CURRENT_DATE)")
    long countThisMonth();
}
