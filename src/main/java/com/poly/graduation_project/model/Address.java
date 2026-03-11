package com.poly.graduation_project.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // ID tỉnh
    private String provinceId;

    // ID quận
    private String districtId;

    // ID phường
    private String wardcode;

    // Địa chỉ cụ thể (số nhà, tên đường)
    @Column(columnDefinition = "nvarchar(255)")
    private String address;

    // Địa chỉ đầy đủ
    @Column(columnDefinition = "nvarchar(255)")
    private String fulladdress;

    // Địa chỉ mặc định
    @Column(name = "is_default")
    private Boolean isDefault = false;

    // Trạng thái còn sử dụng
    private Boolean active = true;

    // Quan hệ với bảng User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}