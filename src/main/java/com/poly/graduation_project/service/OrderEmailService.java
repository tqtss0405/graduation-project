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
                            : "Thanh toán khi nhận hàng (COD)") + "\n\n"
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
                            ? reason.trim() : "Không có lý do cụ thể") + "\n\n"
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
}