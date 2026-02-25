package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.repository.CategoryRepository;

@Controller
public class AdminPageController {
    @Autowired
    CategoryRepository categoryRepo;
    @GetMapping("/admin/dashboard")
    public String dasboard(Model model) {
        return "admin-dashboard";
    }

    @GetMapping("/admin/customers")
    public String customers(Model model) {
        return "admin-customers";
    }
    @GetMapping("/admin/products")
    public String products(Model model) {
        return "admin-products";
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
        @ModelAttribute Product product,
        @RequestParam("mainImage") MultipartFile mainImage,
        @RequestParam("galleryImages") MultipartFile[] galleryImages){
            return "redirect:/admin/products";
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
}