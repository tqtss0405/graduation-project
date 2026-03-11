package com.poly.graduation_project.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.poly.graduation_project.model.Address;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.AddressRepository;

@Service
public class AddressService {

    @Autowired
    AddressRepository addressRepository;

    // Lưu địa chỉ
    public void save(Address address) {
        addressRepository.save(address);
    }

    // Lấy danh sách địa chỉ của user
    public List<Address> getByUser(User user) {
        return addressRepository.findByUser(user);
    }

    // Xóa địa chỉ (xóa mềm)
    public void delete(Integer id) {
        Address address = addressRepository.findById(id).orElse(null);

        if (address != null) {
            address.setActive(false); // ẩn địa chỉ
            addressRepository.save(address);
        }
    }
}