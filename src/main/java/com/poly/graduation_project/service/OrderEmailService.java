package com.poly.graduation_project.service;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async
    public void sendConfirmationEmail(User user, Order order) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(user.getEmail());
            msg.setSubject("BEOBOOKS - Xac nhan don hang #" + order.getId());
            msg.setText(
                "Xin chao " + user.getFullname() + ",\n\n" +
                "Don hang #" + order.getId() + " cua ban da duoc dat thanh cong!\n\n" +
                "Dia chi giao hang: " + order.getAddress() + "\n" +
                "Tong thanh toan: " + String.format("%,.0f", order.getTotal()) + "d\n" +
                "Phuong thuc: " + (order.getPaymentMethod() == 1 ? "VNPay (Da thanh toan)" : "Thanh toan khi nhan hang (COD)") + "\n\n" +
                "Cam on ban da mua sam tai BEOBOOKS!\n" +
                "Chung toi se lien he xac nhan va giao hang som nhat."
            );
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("Loi gui email xac nhan: " + e.getMessage());
        }
    }
}
