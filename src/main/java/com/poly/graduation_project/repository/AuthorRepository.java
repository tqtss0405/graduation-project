package com.poly.graduation_project.repository;

import com.poly.graduation_project.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Integer> {

    // Lấy tác giả đang hiển thị
    List<Author> findByActiveTrueOrderByNameAsc();

    // Lấy tất cả (kể cả ẩn) — dùng cho admin
    List<Author> findAllByOrderByNameAsc();

    // Kiểm tra trùng tên
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    // Tìm kiếm theo tên (active)
    @Query("SELECT a FROM Author a WHERE a.active = true AND LOWER(a.name) LIKE LOWER(CONCAT('%', :kw, '%')) ORDER BY a.name ASC")
    List<Author> searchByName(@Param("kw") String kw);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.authorEntity.id = :authorId")
    long countProductsByAuthorId(@Param("authorId") Integer authorId);

   @Query("SELECT DISTINCT a FROM Author a LEFT JOIN FETCH a.products ORDER BY a.name ASC")
List<Author> findAllWithProducts();
}