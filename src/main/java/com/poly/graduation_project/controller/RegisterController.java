package com.poly.graduation_project.controller;

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

    // 1. KIỂM TRA RỖNG TRƯỚC TIÊN
    // Nếu bất kỳ trường nào bị null hoặc chỉ chứa khoảng trắng
    if (dto.getFullname() == null || dto.getFullname().trim().isEmpty() ||
        dto.getEmail() == null || dto.getEmail().trim().isEmpty() ||
        dto.getPhone() == null || dto.getPhone().trim().isEmpty() ||
        dto.getPassword() == null || dto.getPassword().trim().isEmpty() ||
        dto.getConfirmPassword() == null || dto.getConfirmPassword().trim().isEmpty()) {
        
        model.addAttribute("errorMessage", "Vui lòng điền đầy đủ thông tin!");
        return "register"; 
    }

    // 2. KIỂM TRA LỖI ĐỊNH DẠNG TỪ DTO (@Email, @Pattern, @Size...)
    if (result.hasErrors()) {
        // Lấy câu thông báo lỗi đầu tiên trong DTO để hiển thị
        String firstError = result.getAllErrors().get(0).getDefaultMessage();
        model.addAttribute("errorMessage", firstError);
        return "register";
    }

    // 3. KIỂM TRA LOGIC NGHIỆP VỤ (Mật khẩu khớp, Email tồn tại)
    if (!dto.getPassword().equals(dto.getConfirmPassword())) {
        model.addAttribute("errorMessage", "Mật khẩu xác nhận không khớp!");
        return "register";
    }

    if (userRepository.existsByEmail(dto.getEmail())) {
        model.addAttribute("errorMessage", "Email này đã được đăng ký. Vui lòng chọn email khác!");
        return "register";
    }

    // 4. LƯU VÀO DATABASE NẾU VƯỢT QUA MỌI KIỂM TRA
    User newUser = new User();
    newUser.setFullname(dto.getFullname().trim());
    newUser.setEmail(dto.getEmail().trim().toLowerCase());
    newUser.setPhone(dto.getPhone().trim());
    newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
    newUser.setRole(false);
    newUser.setActive(true);

    userRepository.save(newUser);

    ra.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
    return "redirect:/login/form";
}
}
