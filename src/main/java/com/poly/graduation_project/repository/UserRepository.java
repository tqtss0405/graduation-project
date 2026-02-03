package com.poly.graduation_project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
}
