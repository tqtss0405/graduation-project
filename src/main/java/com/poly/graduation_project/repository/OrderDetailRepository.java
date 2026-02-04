package com.poly.graduation_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.OrderDetail;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {

}
