package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.service.UserService;

@Controller
@RequestMapping("/admin")
public class AdminPageController {

    @Autowired
    private CategoryRepository categoryRepo;

    @Autowired
    private UserService userService;

    // Trang Dashboard
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return "admin-dashboard";
    }

    // Danh sách khách hàng
    @GetMapping("/customers")
    public String customers(Model model) {
        model.addAttribute("customers", userService.getAllCustomers());
        return "admin-customers";
    }

    // Chặn / Mở chặn tài khoản khách hàng
    @PostMapping("/customers/toggle-status/{id}")
    public String toggleCustomerStatus(@PathVariable Integer id) {
        userService.toggleUserStatus(id);
        return "redirect:/admin/customers";
    }
}
