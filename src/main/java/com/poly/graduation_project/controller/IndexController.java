package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.AddressRepository;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.service.SessionService;

import java.util.List;

@Controller
public class IndexController {

    @Autowired
    UserRepository userRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    SessionService sessionService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    CategoryRepository categoryRepository;

    // ================================================
    // Trang chủ: lấy 8 sản phẩm mới nhất (active=true)
    // ================================================
    @GetMapping("/home")
    public String home(Model model) {
        List<Product> products = productRepository.findTop8ByActiveTrueOrderByIdDesc();
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryRepository.findAll());
        return "index";
    }

    // ================================================
    // Trang tất cả sản phẩm
    // ================================================
    @GetMapping("/products")
    public String products(Model model) {
        List<Product> products = productRepository.findByActiveTrue();
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryRepository.findAll());
        return "products";
    }

    // ================================================
    // Trang chi tiết sản phẩm theo slug
    // ================================================
    @GetMapping("/product/{slug}")
    public String productDetail(@PathVariable("slug") String slug, Model model) {
        Product product = productRepository.findBySlug(slug);
        if (product == null)
            return "redirect:/products";

        // Lấy sản phẩm cùng danh mục (gợi ý)
        List<Product> related = productRepository
                .findTop4ByCategoryAndIdNotAndActiveTrue(product.getCategory(), product.getId());

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", related);
        return "product-details";
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
        if (currentUser == null)
            return "redirect:/login/form";
        User user = userRepository.findById(currentUser.getId()).orElse(null);
        model.addAttribute("user", user);
        return "profile";
    }

    @GetMapping("/user/order-details")
    public String orderDetails(Model model) {
        return "order-details";
    }
}