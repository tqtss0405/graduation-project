package com.poly.graduation_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Product;

public interface ProductRepository extends JpaRepository<Product, Integer> {

}
