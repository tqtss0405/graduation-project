package com.poly.graduation_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Favourite;

public interface FavouriteRepository extends JpaRepository<Favourite, Integer> {

}
