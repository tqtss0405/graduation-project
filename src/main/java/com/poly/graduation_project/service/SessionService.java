package com.poly.graduation_project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

@Service
public class SessionService {
    @Autowired
    HttpSession session;

    /**
     * Lưu thông tin người dùng vào session khi đăng nhập thành công
     * @param key Tên định danh (ví dụ: "currentUser")
     * @param value Đối tượng User
     */
    public void setAttribute(String key, Object value) {
        session.setAttribute(key, value);
    }

    /**
     * Lấy thông tin người dùng từ session
     * @param key Tên định danh
     * @return Đối tượng User (cần ép kiểu khi dùng)
     */
    public <T> T getAttribute(String key) {
        return (T) session.getAttribute(key);
    }

    /**
     * Xóa session (Đăng xuất)
     * @param key Tên định danh cần xóa
     */
    public void removeAttribute(String key) {
        session.removeAttribute(key);
    }

    /**
     * Kiểm tra xem người dùng đã đăng nhập chưa
     * @return true nếu đã đăng nhập
     */
    public boolean isAuthenticated() {
        return getAttribute("currentUser") != null;
    }
}
