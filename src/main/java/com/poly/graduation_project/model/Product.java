package com.poly.graduation_project.model;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "nvarchar(250)")
    private String name;

    // Giữ lại trường author (String) để tương thích ngược, nhưng ưu tiên dùng
    // authorEntity
    @Column(columnDefinition = "nvarchar(250)")
    private String author;

    @Column(columnDefinition = "nvarchar(250)")
    private String publisher;

    @Column(columnDefinition = "nvarchar(250)", unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private Long stockQuantity;

    private Boolean active;

    @Column(columnDefinition = "nvarchar(250)")
    private String image;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // Quan hệ với bảng authors (nullable — nếu null thì tác giả = author String
    // hoặc "Ẩn danh")
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = true)
    private Author authorEntity;

    @OneToMany(mappedBy = "product")
    private List<Image> images;

    @OneToMany(mappedBy = "product")
    private List<CartDetail> cartDetails;

    @OneToMany(mappedBy = "product")
    private List<Favourite> favourites;

    @OneToMany(mappedBy = "product")
    private List<OrderDetail> orderDetails;

    /**
     * Trả về tên tác giả hiển thị:
     * - Nếu có authorEntity → dùng tên của entity
     * - Nếu có author (String cũ) → dùng author
     * - Không có gì → "Ẩn danh"
     */
    @Transient
    public String getDisplayAuthor() {
        if (authorEntity != null && Boolean.TRUE.equals(authorEntity.getActive())) {
            return authorEntity.getName();
        }
        if (author != null && !author.isBlank()) {
            return author;
        }
        return "Ẩn danh";
    }
}