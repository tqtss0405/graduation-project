package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.service.UserService;

@Controller
public class RegisterController {
    @Autowired
    UserService userService;

    // Hiển thị trang đăng ký
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "register"; // Trả về file register.html
    }

    // Xử lý dữ liệu đăng ký
    @PostMapping("/register")
    public String processRegister(@ModelAttribute("user") User user, Model model) {
        try {
            userService.save(user);
            // Sau khi đăng ký thành công, chuyển hướng về trang login
            return "redirect:/login/form?success";
        } catch (Exception e) {
            model.addAttribute("error", "Đăng ký thất bại: " + e.getMessage());
            return "register";
        }
    }
}
