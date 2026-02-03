package com.poly.graduation_project.model;

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

@Entity 
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, columnDefinition = "nvarchar(100)")
    private String email;

    @Column(length = 250)
    private String password;

    @Column(columnDefinition = "nvarchar(100)")
    private String fullname;

    @Column(length = 10)
    private String phone;
    @Column(columnDefinition = "nvarchar(250)")
    private String image;
    private Boolean gender;
    private Boolean role;
    private Boolean active;

    @OneToMany(mappedBy = "user")
    private List<Address> addresses;

    @OneToMany(mappedBy = "user")
    private List<CartDetail> cartDetails;

    @OneToMany(mappedBy = "user")
    private List<Favourite> favourites;

    @OneToMany(mappedBy = "user")
    private List<Order> orders;

    @OneToMany(mappedBy = "user")
    private List<Review> reviews;
}
