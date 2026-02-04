package com.poly.graduation_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.poly.graduation_project.model.Address;

public interface AddressRepository extends JpaRepository<Address, Integer> {

}
