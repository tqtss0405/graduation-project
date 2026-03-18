package com.poly.graduation_project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;


@Service // ✅ Đã xóa @Lazy
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

        // LẤY TRỰC TIẾP mật khẩu từ DB
        String passwordFromDB = user.getPassword();

        // Chuyển đổi quyền hạn (Role) từ boolean sang String
        String roleName = (user.getRole() != null && user.getRole()) ? "ADMIN" : "USER";

        // Lấy trạng thái hoạt động của tài khoản (nếu null thì mặc định là true)
        boolean isActive = (user.getActive() != null) ? user.getActive() : true;

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(passwordFromDB)
                .roles(roleName)
                .disabled(!isActive) // QUAN TRỌNG: Báo cho Spring Security biết tài khoản có bị khóa không
                .build();
    }

    public void save(User user) {

    // Chỉ encode khi password chưa mã hóa
    if (!user.getPassword().startsWith("$2a$")) {
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
    }

    if (user.getRole() == null) {
        user.setRole(false);
    }

    userRepository.save(user);
}

    // Thêm các phương thức này vào trong class UserService hiện tại
    public java.util.List<User> getAllCustomers() {
        // Giả sử role = false là khách hàng
        return userRepository.findByRole(false);
    }

    // Hàm xử lý Khóa / Mở khóa tài khoản
    public void toggleUserStatus(Integer id) {
        // Tìm user theo ID
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            // Đảo ngược trạng thái hiện tại (nếu null thì mặc định chuyển thành false - bị
            // chặn)
            boolean currentStatus = (user.getActive() != null) ? user.getActive() : true;
            user.setActive(!currentStatus);
            userRepository.save(user); // Lưu lại vào Database
        }
    }
}