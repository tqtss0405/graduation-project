package com.poly.graduation_project.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.Favourite;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;

import jakarta.transaction.Transactional;

public interface FavouriteRepository extends JpaRepository<Favourite, Integer> {

    List<Favourite> findByUser(User user);

    boolean existsByUserAndProduct(User user, Product product);

    Optional<Favourite> findByUserAndProduct(User user, Product product);

    // ✅ Bắt buộc có @Modifying + @Transactional + @Query mới delete được
    @Modifying
    @Transactional
    @Query("DELETE FROM Favourite f WHERE f.user = :user AND f.product = :product")
    void deleteByUserAndProduct(@Param("user") User user, @Param("product") Product product);
}