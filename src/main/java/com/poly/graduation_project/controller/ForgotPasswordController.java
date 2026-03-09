package com.poly.graduation_project.controller;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Random;

@Controller
public class ForgotPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender mailSender;

    // Mở trang Bước 1
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("step", 1);
        return "forgot-password";
    }

    // Nút Lấy mã OTP (Chạy ngầm bằng JS)
    @PostMapping("/forgot-password/send-otp-ajax")
    @ResponseBody
    public String sendOtpAjax(@RequestParam("email") String email, HttpSession session) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "not_found";
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        session.setAttribute("RESET_OTP", otp);
        session.setAttribute("RESET_EMAIL", email);
        session.setAttribute("RESET_OTP_TIME", System.currentTimeMillis());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Mã OTP khôi phục mật khẩu - BEOBOOKS");
            message.setText("Xin chào " + user.getFullname() + ",\n\n"
                    + "Mã OTP khôi phục mật khẩu của bạn là: " + otp + "\n\n"
                    + "Mã này có hiệu lực trong vòng 1 phút. Không chia sẻ mã này cho ai khác.");
            mailSender.send(message);
            return "success";
        } catch (Exception e) {
            return "error";
        }
    }

    // Nút XÁC NHẬN MÃ OTP (Chuyển sang Bước 2)
    @PostMapping("/forgot-password/verify-otp")
    public String verifyOtp(@RequestParam("email") String email,
            @RequestParam("otp") String otp,
            HttpSession session, Model model) {

        String sessionOtp = (String) session.getAttribute("RESET_OTP");
        Long otpTime = (Long) session.getAttribute("RESET_OTP_TIME");

        // Quay lại bước 1 nếu có lỗi
        model.addAttribute("step", 1);
        model.addAttribute("emailInput", email); // Giữ lại email người dùng đã nhập

        if (sessionOtp == null || otpTime == null) {
            model.addAttribute("error", "Vui lòng bấm 'Lấy mã OTP' trước!");
            return "forgot-password";
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - otpTime > 60000) {
            model.addAttribute("error", "Mã OTP đã hết hạn (Quá 1 phút). Vui lòng lấy mã mới!");
            return "forgot-password";
        }

        if (!sessionOtp.equals(otp)) {
            model.addAttribute("error", "Mã OTP không chính xác!");
            return "forgot-password";
        }

        // Vượt qua kiểm tra -> Cho phép vào Bước 2
        session.setAttribute("OTP_VERIFIED", true);
        session.setAttribute("VERIFIED_EMAIL", email);
        model.addAttribute("step", 2);
        return "forgot-password";
    }

    // Nút LƯU MẬT KHẨU MỚI
    @PostMapping("/forgot-password/reset")
    public String resetPassword(@RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session, Model model) {

        Boolean isVerified = (Boolean) session.getAttribute("OTP_VERIFIED");
        String verifiedEmail = (String) session.getAttribute("VERIFIED_EMAIL");

        // Chống hack: Bắt buộc phải xác thực OTP rồi mới được đổi
        if (isVerified == null || !isVerified || verifiedEmail == null) {
            model.addAttribute("error", "Phiên làm việc không hợp lệ, vui lòng thử lại!");
            model.addAttribute("step", 1);
            return "forgot-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
            model.addAttribute("step", 2);
            return "forgot-password";
        }

        // Lưu mật khẩu
        User user = userRepository.findByEmail(verifiedEmail).orElse(null);
        if (user != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        }

        // Dọn dẹp session
        session.invalidate();

        model.addAttribute("step", 3);
        model.addAttribute("message", "Đổi mật khẩu thành công! Bạn có thể đăng nhập ngay bây giờ.");
        return "forgot-password";
    }
}