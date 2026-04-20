package com.poly.graduation_project.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "authors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "nvarchar(250)", nullable = false)
    private String name;

    @Column(columnDefinition = "nvarchar(500)")
    private String bio;

    @Column(columnDefinition = "nvarchar(250)")
    private String nationality;

    // true = hiển thị, false = ẩn (soft delete)
    private Boolean active = true;

    @OneToMany(mappedBy = "authorEntity")
    @ToString.Exclude
    @JsonIgnore
    private List<Product> products;

    @Transient
    public int getProductCount() {
        return products != null ? products.size() : 0;
    }
}