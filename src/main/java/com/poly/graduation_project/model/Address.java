package com.poly.graduation_project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity 
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "addresses")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer provinceId;
    private Integer districtId;
    private Integer wardcode;

    @Column(columnDefinition = "nvarchar(255)")
    private String address;

    @Column(columnDefinition = "nvarchar(255)")
    private String fulladdress;

    private Boolean isDefault;
    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
