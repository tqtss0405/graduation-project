package com.poly.graduation_project.model;

import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

     @Column(unique = true, columnDefinition = "nvarchar(100)")
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(columnDefinition = "nvarchar(100)")
    private String fullname;

    @Column(length = 10)
    private String phone;
    @Column(columnDefinition = "nvarchar(250)")
    private String image;

    private Boolean gender;

    // true = ADMIN
    // false = USER
    private Boolean role;

    // true = active
    // false = blocked / unverified
    private Boolean active;

    @Column(length = 6)
    private String verificationCode;

    private java.time.LocalDateTime codeExpiry;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<Address> addresses;

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private List<CartDetail> cartDetails;

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private List<Favourite> favourites;

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private List<Order> orders;

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private List<Review> reviews;
}
