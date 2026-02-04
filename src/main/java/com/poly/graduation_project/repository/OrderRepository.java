package com.poly.graduation_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {

}
