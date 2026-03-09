package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.service.SessionService;

@Controller
public class AuthController {
    @Autowired
    UserRepository userRepository;
    @Autowired
    SessionService sessionService;

    // 1. Hiển thị trang đăng nhập
    @GetMapping("/login/form")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {

        // Kiểm tra xem người dùng đã đăng nhập chưa
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Nếu authentication tồn tại VÀ đã xác thực VÀ không phải là người dùng ẩn danh
        // (khách)
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin) {
                return "redirect:/admin/dashboard"; // Admin thì về trang quản trị
            } else {
                return "redirect:/home"; // User thì về trang chủ
            }
        }

        // Nếu có tham số ?error trên URL -> Hiển thị lỗi
        if (error != null) {
            model.addAttribute("message", "Sai tài khoản hoặc mật khẩu!");
            model.addAttribute("alertClass", "alert-danger");
        }
        // Nếu có tham số ?logout -> Hiển thị thông báo đã thoát
        if (logout != null) {
            model.addAttribute("message", "Đăng xuất thành công!");
            model.addAttribute("alertClass", "alert-success");
        }
        return "login"; // Trả về file login.html
    }

    // 2. Xử lý điều hướng sau khi đăng nhập thành công
    // (Mapping này khớp với .defaultSuccessUrl("/login/success") trong
    // SecurityConfig)
    @GetMapping("/login/success")
    public String loginSuccess(Authentication authentication) {
        // 1. Lấy email/username từ đối tượng Authentication
        String email = authentication.getName();

        // 2. Tìm đối tượng User thực sự trong Database
        User user = userRepository.findByEmail(email).orElse(null);

        // 3. Lưu vào Session thông qua SessionService
        if (user != null) {
            // Xóa mật khẩu trước khi lưu vào session để bảo mật (tùy chọn)
            user.setPassword("");
            sessionService.setAttribute("currentUser", user);
        }

        // 4. Điều hướng như cũ
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/home";
    }

    // 3. Xử lý khi đăng nhập thất bại
    @GetMapping("/login/failure")
    public String loginFailure(Model model) {
        model.addAttribute("message", "Sai tên đăng nhập hoặc mật khẩu!");
        model.addAttribute("alertClass", "alert-danger");
        return "login";
    }

    // 4. Xử lý sau khi đăng xuất (logoutSuccessUrl)
    @GetMapping("/login/exit")
    public String logoutSuccess(Model model) {
        model.addAttribute("message", "Bạn đã đăng xuất khỏi hệ thống.");
        model.addAttribute("alertClass", "alert-info");
        return "login";
    }

    // 5. Trang từ chối truy cập (403)
    @GetMapping("/unauthorized")
    public String accessDenied(Model model) {
        model.addAttribute("message", "Bạn không có quyền truy cập trang này!");
        model.addAttribute("alertClass", "alert-warning");
        return "login";
    }
}