package com.poly.graduation_project.controller;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.dto.UserRegisterDTO;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.service.AuthEmailService;
import com.poly.graduation_project.service.UserService;

import jakarta.validation.Valid;

@Controller
public class RegisterController {
    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuthEmailService authEmailService;

    // Hiển thị trang đăng ký
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerDTO", new UserRegisterDTO());
        return "register"; // Trả về file register.html
    }

    // Xử lý dữ liệu đăng ký
    @PostMapping("/register")
    public String processRegister(@Valid @ModelAttribute("registerDTO") UserRegisterDTO dto,
                                  BindingResult result, 
                                  Model model,
                                  RedirectAttributes ra) {

        // 1. KIỂM TRA LỖI ĐỊNH DẠNG TỪ DTO (@NotBlank, @Email, @Pattern, @Size...)
        if (result.hasErrors()) {
            String firstError = result.getAllErrors().get(0).getDefaultMessage();
            model.addAttribute("errorMessage", firstError);
            return "register";
        }

        // 2. KIỂM TRA LOGIC NGHIỆP VỤ (Mật khẩu khớp, Email tồn tại)
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            model.addAttribute("errorMessage", "Mật khẩu xác nhận không khớp!");
            return "register";
        }

        User existingUser = userRepository.findByEmail(dto.getEmail()).orElse(null);
        if (existingUser != null) {
            if (Boolean.TRUE.equals(existingUser.getActive())) {
                model.addAttribute("errorMessage", "Email này đã được đăng ký. Vui lòng chọn email khác!");
                return "register";
            } else {
                // Nếu email đã đăng ký nhưng chưa active (chưa xác nhận OTP)
                // Cập nhật lại thông tin mới và gửi lại OTP
                updateAndSendOtp(existingUser, dto);
                ra.addFlashAttribute("message", "Tài khoản chưa được xác thực. Chúng tôi đã gửi một mã OTP mới đến email của bạn.");
                ra.addFlashAttribute("alertClass", "alert-warning");
                return "redirect:/verify-otp?email=" + existingUser.getEmail();
            }
        }

        // 3. LƯU VÀO DATABASE VÀ GỬI OTP NẾU VƯỢT QUA MỌI KIỂM TRA
        User newUser = new User();
        updateAndSendOtp(newUser, dto);

        ra.addFlashAttribute("message", "Đăng ký thành công! Vui lòng kiểm tra email để lấy mã xác nhận.");
        ra.addFlashAttribute("alertClass", "alert-success");
        return "redirect:/verify-otp?email=" + newUser.getEmail();
    }

    private void updateAndSendOtp(User user, UserRegisterDTO dto) {
        user.setFullname(dto.getFullname().trim());
        user.setEmail(dto.getEmail().trim().toLowerCase());
        user.setPhone(dto.getPhone().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(false);
        user.setActive(false); // Chưa xác thực

        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setVerificationCode(otp);
        user.setCodeExpiry(LocalDateTime.now().plusMinutes(15)); // Hết hạn sau 15 phút

        userRepository.save(user);

        authEmailService.sendOtpEmail(user.getEmail(), otp);
    }

    // ─── HIỂN THỊ TRANG NHẬP OTP ───────────────────────────────────────────
    @GetMapping("/verify-otp")
    public String showVerifyOtpForm(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        return "verify-otp"; // Trả về file verify-otp.html
    }

    // ─── XỬ LÝ KIỂM TRA OTP ────────────────────────────────────────────────
    @PostMapping("/verify-otp")
    public String processVerifyOtp(@RequestParam("email") String email,
                                   @RequestParam("otp1") String otp1,
                                   @RequestParam("otp2") String otp2,
                                   @RequestParam("otp3") String otp3,
                                   @RequestParam("otp4") String otp4,
                                   @RequestParam("otp5") String otp5,
                                   @RequestParam("otp6") String otp6,
                                   RedirectAttributes ra,
                                   Model model) {
        String otp = otp1 + otp2 + otp3 + otp4 + otp5 + otp6;
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            model.addAttribute("errorMessage", "Không tìm thấy người dùng!");
            model.addAttribute("email", email);
            return "verify-otp";
        }

        if (Boolean.TRUE.equals(user.getActive())) {
            ra.addFlashAttribute("message", "Tài khoản của bạn đã được xác thực trước đó.");
            ra.addFlashAttribute("alertClass", "alert-info");
            return "redirect:/login/form";
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(otp)) {
            model.addAttribute("errorMessage", "Mã xác nhận không chính xác!");
            model.addAttribute("email", email);
            return "verify-otp";
        }

        if (user.getCodeExpiry() == null || user.getCodeExpiry().isBefore(LocalDateTime.now())) {
            model.addAttribute("errorMessage", "Mã xác nhận đã hết hạn. Vui lòng yêu cầu gửi lại mã mới!");
            model.addAttribute("email", email);
            return "verify-otp";
        }

        // Kích hoạt tài khoản
        user.setActive(true);
        user.setVerificationCode(null);
        user.setCodeExpiry(null);
        userRepository.save(user);

        ra.addFlashAttribute("message", "Xác thực email thành công! Bây giờ bạn có thể đăng nhập.");
        ra.addFlashAttribute("alertClass", "alert-success");
        return "redirect:/login/form";
    }

    // ─── XỬ LÝ GỬI LẠI OTP ────────────────────────────────────────────────
    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam("email") String email, RedirectAttributes ra) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && !Boolean.TRUE.equals(user.getActive())) {
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setVerificationCode(otp);
            user.setCodeExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

            authEmailService.sendOtpEmail(user.getEmail(), otp);

            ra.addFlashAttribute("message", "Đã gửi lại mã xác nhận mới đến email của bạn.");
            ra.addFlashAttribute("alertClass", "alert-success");
        } else {
            ra.addFlashAttribute("message", "Không thể gửi lại mã xác nhận.");
            ra.addFlashAttribute("alertClass", "alert-danger");
        }
        return "redirect:/verify-otp?email=" + email;
    }
}
