package com.poly.graduation_project.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(columnDefinition = "nvarchar(255)")
    private String address;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    private Boolean freeShip;
    private LocalDateTime createAt;
    private Integer paymentMethod;
    private Integer paymentStatus;
    private Integer status;

    @Column(columnDefinition = "nvarchar(255)")
    private String note;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalDiscount;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @OneToMany(mappedBy = "order")
    private List<OrderDetail> orderDetails;
}
