package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.service.UserService;

@Controller
@RequestMapping("/register")
public class RegisterController {

    @Autowired
    private UserService userService;

    // =============================
    // HIỂN THỊ FORM
    // =============================
    @GetMapping
    public String showForm(@ModelAttribute("user") User user) {
        return "register";
    }

    // =============================
    // XỬ LÝ ĐĂNG KÝ
    // =============================
    @PostMapping
    public String processRegister(
            @Valid @ModelAttribute("user") User user,
            BindingResult result,
            @RequestParam("confirmPassword") String confirmPassword) {

        // 1️⃣ Kiểm tra confirm password
        if (!user.getPassword().equals(confirmPassword)) {
            result.rejectValue(
                    "password",
                    "error.user",
                    "Mật khẩu xác nhận không khớp!"
            );
        }

        // 2️⃣ Nếu validation lỗi
        if (result.hasErrors()) {
            return "register";
        }

        try {
            userService.save(user);
            return "redirect:/login/form?success";

        } catch (RuntimeException e) {

            // 3️⃣ Nếu email trùng
            result.rejectValue(
                    "email",
                    "error.user",
                    e.getMessage()
            );

            return "register";
        }
    }
}