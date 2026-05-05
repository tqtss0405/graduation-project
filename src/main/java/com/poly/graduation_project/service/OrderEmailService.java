package com.poly.graduation_project.service;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderEmailService {

    @Autowired
    private JavaMailSender mailSender;

    // ─── Gửi email xác nhận đặt hàng ───────────────────────────────────────
    @Async
    public void sendConfirmationEmail(User user, Order order) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("BEOBOOKS - Xác nhận đơn hàng #" + order.getId());

            String body = "Xin chào " + user.getFullname() + ",\n\n"
                    + "Đơn hàng #" + order.getId() + " của bạn đã được đặt thành công!\n\n"
                    + "Địa chỉ giao hàng : " + order.getAddress() + "\n"
                    + "Tổng thanh toán   : " + String.format("%,.0f", order.getTotal()) + "đ\n"
                    + "Phương thức       : " + (order.getPaymentMethod() == 1
                            ? "VNPay (Đã thanh toán)"
                            : "Thanh toán khi nhận hàng (COD)")
                    + "\n\n"
                    + "Cảm ơn bạn đã mua sắm tại BEOBOOKS!\n"
                    + "Chúng tôi sẽ liên hệ xác nhận và giao hàng sớm nhất.";

            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email xác nhận: " + e.getMessage());
        }
    }

    // ─── Gửi email thông báo hủy đơn (admin hủy) ───────────────────────────
    @Async
    public void sendCancellationEmail(User user, Order order, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject("BEOBOOKS - Đơn hàng #" + order.getId() + " đã bị hủy");

            String refundNote = "";
            if (order.getPaymentMethod() != null && order.getPaymentMethod() == 1) {
                refundNote = "\nVì bạn đã thanh toán qua VNPay, chúng tôi sẽ liên hệ để hoàn tiền "
                        + "trong vòng 3–5 ngày làm việc.\n";
            }

            String body = "Xin chào " + user.getFullname() + ",\n\n"
                    + "Đơn hàng #" + order.getId() + " của bạn đã bị HỦY bởi chủ shop.\n\n"
                    + "Lý do hủy         : " + (reason != null && !reason.trim().isEmpty()
                            ? reason.trim()
                            : "Không có lý do cụ thể")
                    + "\n\n"
                    + "Thông tin đơn hàng:\n"
                    + "  • Địa chỉ giao hàng : " + order.getAddress() + "\n"
                    + "  • Tổng giá trị      : " + String.format("%,.0f", order.getTotal()) + "đ\n"
                    + "  • Phương thức TT    : " + (order.getPaymentMethod() == 1 ? "VNPay" : "COD") + "\n"
                    + refundNote
                    + "\nNếu có thắc mắc, vui lòng liên hệ bộ phận hỗ trợ của BEOBOOKS.\n\n"
                    + "Xin lỗi vì sự bất tiện này!\n"
                    + "BEOBOOKS Team";

            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email hủy đơn: " + e.getMessage());
        }
    }

    @Async
    public void sendRefundedEmail(User user, Order order) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("hn8438697@gmail.com", "BEOBOOKS");
            helper.setTo(user.getEmail());
            helper.setSubject("BEOBOOKS - Hoàn tiền đơn hàng #" + order.getId() + " thành công ✅");

            String bankInfo = "";
            if (order.getBankAccount() != null && !order.getBankAccount().isBlank()) {
                String[] parts = order.getBankAccount().split("\\|");
                bankInfo = "<tr><th>Tài khoản nhận:</th><td><strong>"
                        + String.join(" — ", java.util.Arrays.stream(parts)
                                .map(String::trim).toArray(String[]::new))
                        + "</strong></td></tr>";
            }

            String htmlBody = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;"
                    + "padding:20px;border:1px solid #e0e0e0;border-radius:10px;'>"
                    + "<h2 style='color:#28a745;text-align:center;'>"
                    + "<i>✅</i> Hoàn tiền thành công!</h2>"
                    + "<p>Xin chào <strong>" + user.getFullname() + "</strong>,</p>"
                    + "<p>Chúng tôi đã hoàn tiền thành công cho đơn hàng <strong>#"
                    + order.getId() + "</strong> của bạn.</p>"
                    + "<table style='width:100%;border-collapse:collapse;margin:16px 0;'>"
                    + "<tr><th style='text-align:left;padding:6px;background:#f8f9fa;width:140px;'>Mã đơn hàng:</th>"
                    + "<td style='padding:6px;'>#" + order.getId() + "</td></tr>"
                    + "<tr><th style='text-align:left;padding:6px;background:#f8f9fa;'>Số tiền hoàn:</th>"
                    + "<td style='padding:6px;color:#dc3545;font-weight:bold;'>"
                    + String.format("%,.0fđ", order.getTotal()) + "</td></tr>"
                    + bankInfo
                    + "</table>"
                    + "<div style='background:#d4edda;border:1px solid #c3e6cb;border-radius:8px;"
                    + "padding:12px 16px;margin:16px 0;color:#155724;'>"
                    + "<strong>⏰ Lưu ý:</strong> Tiền sẽ xuất hiện trong tài khoản của bạn "
                    + "trong <strong>1–3 ngày làm việc</strong> tùy ngân hàng."
                    + "</div>"
                    + "<p>Cảm ơn bạn đã tin tưởng và mua sắm tại <strong>BEOBOOKS</strong>!</p>"
                    + "<p>Trân trọng,<br><strong>BEOBOOKS Team</strong></p>"
                    + "</div>";

            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email hoàn tiền: " + e.getMessage());
        }
    }
}