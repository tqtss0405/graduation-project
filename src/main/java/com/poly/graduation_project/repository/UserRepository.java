package com.poly.graduation_project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Lấy danh sách user theo quyền (false = Khách hàng, true = Admin)
    List<User> findByRole(Boolean role);
}
