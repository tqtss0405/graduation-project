package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.poly.graduation_project.model.Address;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.service.AddressService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/profile/address")
public class AddressController {

    @Autowired
    AddressService addressService;

    // Thêm địa chỉ
    @PostMapping("/add")
    public String addAddress(@ModelAttribute Address address, HttpSession session) {

        User user = (User) session.getAttribute("currentUser");

        if (user == null) {
            return "redirect:/login";
        }

        address.setUser(user);
        address.setActive(true);

        addressService.save(address);

        return "redirect:/user/profile";
    }

    // Xóa địa chỉ
    @GetMapping("/delete/{id}")
    public String deleteAddress(@PathVariable Integer id) {

        addressService.delete(id);

        return "redirect:/user/profile";
    }
}