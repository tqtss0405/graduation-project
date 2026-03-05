package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.poly.graduation_project.repository.CategoryRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AdminPageController {
    @Autowired
    CategoryRepository categoryRepo;
    @Autowired
    private com.poly.graduation_project.service.UserService userService;

    @GetMapping("/admin/dashboard")
    public String dasboard(Model model) {
        return "admin-dashboard";
    }

    @GetMapping("/admin/customers")
    public String customers(Model model) {
        // Lấy danh sách khách hàng và đẩy lên view
        model.addAttribute("customers", userService.getAllCustomers());
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

    @GetMapping("/admin/vouchers")
    public String vouchers(Model model) {
        return "admin-vouchers";
    }

    @GetMapping("/admin/reviews")
    public String reviews(Model model) {
        return "admin-reviews";
    }

    // Xử lý khi bấm nút Chặn / Mở chặn
    @PostMapping("/admin/customers/toggle-status/{id}")
    public String toggleCustomerStatus(@PathVariable("id") Integer id) {
        userService.toggleUserStatus(id);
        // Làm xong thì load lại trang danh sách khách hàng
        return "redirect:/admin/customers";
    }
}