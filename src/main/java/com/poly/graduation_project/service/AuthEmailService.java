package com.poly.graduation_project.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuthEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("hn8438697@gmail.com", "BEOBOOKS");
            helper.setTo(toEmail);
            helper.setSubject("BEOBOOKS - Mã xác nhận đăng ký tài khoản");

            String htmlBody = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>"
                    + "<h2 style='color: #28a745; text-align: center;'>Chào mừng đến với BEOBOOKS!</h2>"
                    + "<p>Xin chào,</p>"
                    + "<p>Bạn vừa đăng ký một tài khoản trên hệ thống của chúng tôi. Để hoàn tất việc đăng ký, vui lòng sử dụng mã xác nhận (OTP) gồm 6 chữ số dưới đây:</p>"
                    + "<div style='text-align: center; margin: 30px 0;'>"
                    + "<span style='font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #1a1a2e; background-color: #f8f9fa; padding: 15px 25px; border-radius: 8px; border: 2px dashed #28a745;'>"
                    + otp
                    + "</span>"
                    + "</div>"
                    + "<p>Mã này sẽ hết hạn trong vòng <strong>15 phút</strong>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>"
                    + "<p>Trân trọng,<br><strong>BEOBOOKS Team</strong></p>"
                    + "<hr style='border: none; border-top: 1px solid #e0e0e0; margin-top: 20px;'>"
                    + "<p style='font-size: 12px; color: #888; text-align: center;'>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.</p>"
                    + "</div>";

            helper.setText(htmlBody, true); // true = isHtml
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email OTP: " + e.getMessage());
        }
    }
}
