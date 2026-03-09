package com.poly.graduation_project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Address;
import com.poly.graduation_project.model.User;

public interface AddressRepository extends JpaRepository<Address, Integer> {
  // Lấy tất cả địa chỉ của user
    List<Address> findByUser(User user);

    // Lấy địa chỉ mặc định
    Optional<Address> findByUserAndIsDefaultTrue(User user);
}
