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

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100)
    private String fullname;

    @Column(length = 10)
    private String phone;

    private String image;

    private Boolean gender;

    // true = ADMIN
    // false = USER
    private Boolean role;

    // true = active
    // false = blocked
    private Boolean active;

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
