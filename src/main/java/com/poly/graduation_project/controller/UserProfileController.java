package com.poly.graduation_project.controller;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user/profile")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    // Hiển thị trang profile
    @GetMapping
    public String showProfile(HttpSession session, Model model) {

        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(currentUser.getId()).orElse(null);

        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("user", user);

        return "profile";
    }

    // Cập nhật thông tin và avatar
    @PostMapping("/update")
    public String updateProfile(
            HttpSession session,
            @RequestParam String fullname,
            @RequestParam String phone,
            @RequestParam Boolean gender,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {

        try {

            User currentUser = (User) session.getAttribute("currentUser");

            if (currentUser == null) {
                return "redirect:/login";
            }

            User user = userRepository.findById(currentUser.getId()).orElse(null);

            if (user == null) {
                session.invalidate();
                return "redirect:/login";
            }

            user.setFullname(fullname);
            user.setPhone(phone);
            user.setGender(gender);

            // Upload avatar
            if (imageFile != null && !imageFile.isEmpty()) {

                String uploadDir = System.getProperty("user.dir") + "/uploads/avatar/";

                File dir = new File(uploadDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String originalName = imageFile.getOriginalFilename();
                String fileName = System.currentTimeMillis() + "_" + originalName.replaceAll("\\s+", "_");

                File saveFile = new File(uploadDir + fileName);

                imageFile.transferTo(saveFile);

                user.setImage("/img/avatar/" + fileName);
            }

            userRepository.save(user);

            session.setAttribute("currentUser", user);

            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin thành công!");

        } catch (Exception e) {

            e.printStackTrace();

            redirectAttributes.addFlashAttribute("errorMessage", "Có lỗi xảy ra khi cập nhật!");
        }

        return "redirect:/user/profile";
    }
}