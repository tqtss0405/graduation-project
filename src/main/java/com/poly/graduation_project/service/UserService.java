package com.poly.graduation_project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.UserRepository;

import java.util.List;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ===== LOGIN SPRING SECURITY =====
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Không tìm thấy người dùng: " + email));

        String roleName = (user.getRole() != null && user.getRole()) ? "ADMIN" : "USER";

        boolean isActive = (user.getActive() != null) ? user.getActive() : true;

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(roleName)
                .disabled(!isActive)
                .build();
    }

    // ===== LƯU USER =====
    public void save(User user) {

        // Nếu là user mới thì encode password
        if (user.getId() == null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        // Nếu chưa có role thì mặc định là USER
        if (user.getRole() == null) {
            user.setRole(false);
        }

        // Nếu chưa có trạng thái thì mặc định active
        if (user.getActive() == null) {
            user.setActive(true);
        }

        userRepository.save(user);
    }

    // ===== LẤY DANH SÁCH KHÁCH HÀNG =====
    public List<User> getAllCustomers() {
        return userRepository.findByRole(false);
    }

    // ===== KHÓA / MỞ KHÓA USER =====
    public void toggleUserStatus(Integer id) {

        User user = userRepository.findById(id).orElse(null);

        if (user != null) {

            boolean currentStatus = (user.getActive() != null) ? user.getActive() : true;

            user.setActive(!currentStatus);

            userRepository.save(user);
        }
    }

    // ===== LẤY USER THEO ID =====
    public User getUserById(Integer id) {
        return userRepository.findById(id).orElse(null);
    }

    // ===== XÓA USER =====
    public void deleteUser(Integer id) {
        userRepository.deleteById(id);
    }

}
