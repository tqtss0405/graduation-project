package com.poly.graduation_project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.User;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    // Lấy đơn hàng theo user, sắp xếp mới nhất
    List<Order> findByUserOrderByCreateAtDesc(User user);
    
    // Lấy đơn hàng đã hoàn thành của user (status = 4)
    @Query("SELECT o FROM Order o WHERE o.user = :user AND o.status = 4 ORDER BY o.createAt DESC")
    List<Order> findCompletedOrdersByUser(@Param("user") User user);

    @Query("SELECT o FROM Order o ORDER BY o.createAt DESC")
    List<Order> findAllByOrderByCreateAtDesc();

    @Query("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createAt DESC")
    List<Order> findByStatusOrderByCreateAtDesc(@Param("status") Integer status);

    List<Order> findByUserAndStatus(User user, Integer status);
}
