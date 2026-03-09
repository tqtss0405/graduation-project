package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.service.UserService;

@Controller
public class AdminPageController {
    @Autowired
    CategoryRepository categoryRepo;
    @Autowired
    UserService userService;
    
    @GetMapping("/admin/dashboard")
    public String dasboard(Model model) {
        return "admin-dashboard";
    }

    @GetMapping("/admin/customers")
    public String customers(Model model) {
        return "admin-customers";
    }
    @GetMapping("/admin/orders")
    public String orders(Model model) {
        return "admin-orders";
    }
    @GetMapping("/admin/reports")
    public String reports(Model model) {
        return "admin-reports";
    }
    @PostMapping("/admin/customers/toggle-status/{id}")
    public String toggleCustomerStatus(@PathVariable("id") Integer id) {
        userService.toggleUserStatus(id);
        // Làm xong thì load lại trang danh sách khách hàng
        return "redirect:/admin/customers";
    }
}