package com.poly.graduation_project.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {

    // Tìm user theo email (login)
    Optional<User> findByEmail(String email);

    // Kiểm tra email đã tồn tại chưa (đăng ký)
    boolean existsByEmail(String email);

    // Lấy danh sách user theo quyền
    // false = USER
    // true = ADMIN
    List<User> findByRole(Boolean role);

    // Tìm kiếm user theo email
    List<User> findByEmailContaining(String keyword);

    // Phân trang danh sách user
    Page<User> findAll(Pageable pageable);
}
