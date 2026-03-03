package com.poly.graduation_project.controller;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/user") // Bắt buộc phải có chữ /user ở đầu để Spring Security bảo vệ
public class ChangePasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HttpSession session;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 1. Mở trang Đổi Mật Khẩu
    @GetMapping("/change-password")
    public String showChangePasswordForm() {
        return "change-password";
    }

    // 2. Xử lý khi bấm nút "Xác Nhận Thay Đổi"
    @PostMapping("/change-password")
    public String processChangePassword(
            @RequestParam("currentPass") String currentPass,
            @RequestParam("newPass") String newPass,
            @RequestParam("confirmPass") String confirmPass,
            Model model) {

        // Lấy thông tin user đang đăng nhập từ Session
        User user = (User) session.getAttribute("currentUser");

        if (user == null) {
            return "redirect:/login/form"; // Nếu rớt mạng/mất session thì bắt đăng nhập lại
        }

        // Kiểm tra 1: Mật khẩu cũ có đúng không?
        if (!passwordEncoder.matches(currentPass, user.getPassword())) {
            model.addAttribute("message", "Mật khẩu hiện tại không chính xác!");
            return "change-password";
        }

        // Kiểm tra 2: Mật khẩu mới và xác nhận có khớp không?
        if (!newPass.equals(confirmPass)) {
            model.addAttribute("message", "Xác nhận mật khẩu mới không khớp!");
            return "change-password";
        }

        // Lưu mật khẩu mới vào Database
        user.setPassword(passwordEncoder.encode(newPass));
        userRepository.save(user);

        // Cập nhật lại user mới vào session
        session.setAttribute("currentUser", user);

        model.addAttribute("message", "Đổi mật khẩu thành công!");
        return "change-password";
    }
}