package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.AddressRepository;
import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.service.SessionService;



@Controller
public class IndexController {
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired SessionService sessionService;
    @GetMapping("/home")
    public String home(Model model) {
        return "index";
    }

    @GetMapping("/products") 
    public String products(Model model) {
        return "products";
    }

    @GetMapping("/about")
    public String about(Model model) {
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        return "contact";
    }

    @GetMapping("/blog")
    public String blog(Model model) {
        return "blog";
    }

    @GetMapping("/product-details")
    public String productDetail(Model model) {
        return "product-details";
    }
    @GetMapping("/user/cart")
    public String cart(Model model) {
        return "cart";
    }

    @GetMapping("/user/checkout")
    public String checkout(Model model) {
        return "checkout";
    }
    @GetMapping("/user/favourites")
    public String favourites(Model model) {
        return "favourites";
    }
    
    @GetMapping("/user/profile")
    public String profile(Model model) {
        User currentUser = sessionService.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login/form";

        // Lấy lại user từ DB để có list addresses mới nhất
        User user = userRepository.findById(currentUser.getId()).orElse(null);
        model.addAttribute("user", user);
        return "profile"; // Trả về file profile.html
    }

    @GetMapping("/user/order-details")
    public String orderDetails(Model model) {
        return "order-details";
    }
}
