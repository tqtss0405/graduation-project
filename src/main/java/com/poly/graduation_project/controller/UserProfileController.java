package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user/profile")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    // Hiển thị profile
    @GetMapping
    public String showProfile(HttpSession session, Model model) {

        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            return "redirect:/login";
        }

        // Lấy user mới nhất từ DB
        User user = userRepository.findById(currentUser.getId()).orElse(null);

        model.addAttribute("user", user);

        return "profile";
    }

    // Cập nhật profile
    @PostMapping("/update")
    public String updateProfile(
            HttpSession session,
            @RequestParam String fullname,
            @RequestParam String phone,
            @RequestParam Boolean gender,
            RedirectAttributes redirectAttributes) {

        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            return "redirect:/login";
        }

        // Lấy user từ database
        User user = userRepository.findById(currentUser.getId()).orElse(null);

        if (user == null) {
            return "redirect:/login";
        }

        // Cập nhật thông tin
        user.setFullname(fullname);
        user.setPhone(phone);
        user.setGender(gender);

        userRepository.save(user);

        // Cập nhật lại session
        session.setAttribute("currentUser", user);

        // Thông báo thành công
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin thành công!");

        return "redirect:/user/profile";
    }
}