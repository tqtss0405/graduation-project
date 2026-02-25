package com.poly.graduation_project.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Data
@Table(name = "vouchers")
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 10)
    private String code;
    @Column(columnDefinition = "nvarchar(250)")
    private String name;
    private Integer discount;
    private Integer quantity;

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endAt;

    private Boolean active;
    
    @OneToMany(mappedBy = "voucher")
    private List<Order> orders;

}
