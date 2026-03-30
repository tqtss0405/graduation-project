package com.poly.graduation_project.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // Dùng cùng thư mục upload với ProductController (/img/**)
    @Value("${app.upload.dir}")
    private String uploadDir;

    // ──────────────────────────────────────────────────────────────
    // GET: Hiển thị trang profile
    // ──────────────────────────────────────────────────────────────
    @GetMapping
    public String showProfile(HttpSession session, Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        // Luôn load lại từ DB để có dữ liệu mới nhất (kể cả địa chỉ)
        User user = userRepository.findById(currentUser.getId()).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        return "profile";
    }

    // ──────────────────────────────────────────────────────────────
    // POST: Cập nhật thông tin cá nhân (và avatar nếu có)
    // ──────────────────────────────────────────────────────────────
    @PostMapping("/update")
    public String updateProfile(
            HttpSession session,
            @RequestParam("fullname") String fullname,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "gender", required = false) Boolean gender,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            RedirectAttributes ra) {

        try {
            User currentUser = (User) session.getAttribute("currentUser");
            if (currentUser == null) return "redirect:/login";

            User user = userRepository.findById(currentUser.getId()).orElse(null);
            if (user == null) {
                session.invalidate();
                return "redirect:/login";
            }

            // Validate tên không được rỗng
            if (fullname == null || fullname.trim().isEmpty()) {
                ra.addFlashAttribute("errorMessage", "Họ và tên không được để trống!");
                return "redirect:/user/profile";
            }

            user.setFullname(fullname.trim());
            user.setPhone(phone != null ? phone.trim() : null);
            user.setGender(gender);

            // Upload avatar nếu có file mới
            if (imageFile != null && !imageFile.isEmpty()) {
                // Giới hạn 5MB
                if (imageFile.getSize() > 5 * 1024 * 1024) {
                    ra.addFlashAttribute("errorMessage", "Ảnh đại diện phải nhỏ hơn 5MB!");
                    return "redirect:/user/profile";
                }

                String originalName  = imageFile.getOriginalFilename();
                String ext = (originalName != null && originalName.contains("."))
                        ? originalName.substring(originalName.lastIndexOf("."))
                        : ".jpg";

                // Tên file duy nhất, lưu vào uploadDir (cùng nơi với ảnh sản phẩm)
                String fileName = "avatar_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;

                Path uploadPath = Paths.get(uploadDir);
                Files.createDirectories(uploadPath);
                imageFile.transferTo(uploadPath.resolve(fileName).toFile());

                // URL serve qua WebConfig: /img/**  →  uploadDir/
                user.setImage("/img/" + fileName);
            }

            userRepository.save(user);
            session.setAttribute("currentUser", user); // cập nhật session

            ra.addFlashAttribute("successMessage", "Cập nhật thông tin thành công!");

        } catch (IOException e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Lỗi khi tải ảnh lên, vui lòng thử lại!");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Có lỗi xảy ra: " + e.getMessage());
        }

        return "redirect:/user/profile";
    }
}