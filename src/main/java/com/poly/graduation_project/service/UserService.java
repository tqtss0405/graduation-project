package com.poly.graduation_project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;

@Service  // ✅ Đã xóa @Lazy
public class UserService implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Tìm kiếm user từ DB theo email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng: " + email));

        // LẤY TRỰC TIẾP mật khẩu từ DB (Giả sử trong DB đã lưu mật khẩu BCrypt)
        // Tuyệt đối không encode lại tại đây
        String passwordFromDB = user.getPassword();

        // Chuyển đổi quyền hạn (Role) từ boolean sang String
        String roleName = user.getRole() ? "ADMIN" : "USER";

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(passwordFromDB)
                .roles(roleName) // Spring Security tự thêm tiền tố "ROLE_"
                .build();
    }

    public void save(User user) {
        // Mã hóa mật khẩu từ plain text sang BCrypt
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Thiết lập quyền mặc định là USER (false) nếu chưa có
        if (user.getRole() == null) {
            user.setRole(false);
        }

        userRepository.save(user);
    }
}