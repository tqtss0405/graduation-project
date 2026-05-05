package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.OrderRepository;
import com.poly.graduation_project.repository.ProductRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller
@RequestMapping("/user/order-details")
public class UserOrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    // ================================================
    // POST: User xác nhận đã nhận hàng (status 3 → 4)
    // ================================================
    @PostMapping("/confirm-received/{id}")
    public String confirmReceived(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
        Order order = orderRepository.findById(id).orElse(null);

        if (order == null || !order.getUser().getId().equals(currentUser.getId())) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
            return "redirect:/user/order-details";
        }

        if (order.getStatus() != 3) {
            ra.addFlashAttribute("errorMessage", "Đơn hàng không ở trạng thái có thể xác nhận!");
            return "redirect:/user/order-details";
        }

        order.setStatus(4);
        orderRepository.save(order);

        ra.addFlashAttribute("successMessage", "Xác nhận nhận hàng thành công! Cảm ơn bạn đã mua sắm tại BEOBOOKS 🎉");
        return "redirect:/user/order-details";
    }

    // ================================================
    // POST: User hủy đơn (chỉ hủy được khi status 0 hoặc 1)
    // ================================================
    // Sửa method cancelOrder — thêm @RequestParam reason
@PostMapping("/cancel/{id}")
@Transactional
public String cancelOrder(
        @PathVariable Integer id,
        @RequestParam(value = "cancelReason", required = false) String cancelReason,
        @RequestParam(value = "bankName",    required = false) String bankName,
        @RequestParam(value = "bankNumber",  required = false) String bankNumber,
        @RequestParam(value = "bankHolder",  required = false) String bankHolder,
        HttpSession session,
        RedirectAttributes ra) {

    User currentUser = (User) session.getAttribute("currentUser");
    Order order = orderRepository.findById(id).orElse(null);

    if (order == null || !order.getUser().getId().equals(currentUser.getId())) {
        return "redirect:/user/order-details";
    }

    if (order.getStatus() != 0 && order.getStatus() != 1) {
        ra.addFlashAttribute("errorMessage", "Không thể hủy đơn hàng này!");
        return "redirect:/user/order-details";
    }

    // Restock
    if (order.getOrderDetails() != null) {
        for (OrderDetail od : order.getOrderDetails()) {
            var product = od.getProduct();
            product.setStockQuantity(product.getStockQuantity() + od.getQuantity());
            productRepository.save(product);
        }
    }

    // Ghi lý do
    String reasonText = (cancelReason != null && !cancelReason.trim().isEmpty())
            ? cancelReason.trim() : "Không có lý do";
    String cancelNote = "[KHÁCH HUY] Ly do: " + reasonText;
    String currentNote = (order.getNote() != null && !order.getNote().isBlank())
            ? order.getNote() + " | " + cancelNote : cancelNote;

    // ✅ Nếu VNPay đã thanh toán → lưu bank account và chuyển sang status 6 (Y/c hoàn tiền)
    boolean isVnpayPaid = order.getPaymentMethod() != null
            && order.getPaymentMethod() == 1
            && order.getPaymentStatus() != null
            && order.getPaymentStatus() == 1;

    if (isVnpayPaid) {
        if (bankName != null && !bankName.isBlank()
                && bankNumber != null && !bankNumber.isBlank()
                && bankHolder != null && !bankHolder.isBlank()) {
            String bankInfo = bankName.trim() + " | " + bankNumber.trim() + " | " + bankHolder.trim();
            order.setBankAccount(bankInfo);
        }
        currentNote += " | [CẦN HOÀN TIỀN VNPAY]";
    }

    order.setNote(currentNote);
    // Đơn VNPay đã thanh toán → chuyển sang status 6 (Y/c hoàn tiền), ngược lại status 5 (Đã hủy)
    order.setStatus(isVnpayPaid ? 6 : 5);
    orderRepository.save(order);

    ra.addFlashAttribute("successMessage",
            isVnpayPaid
            ? "Đã hủy đơn! Yêu cầu hoàn tiền đã được gửi. Chúng tôi sẽ hoàn tiền VNPay trong 1-3 ngày làm việc."
            : "Đã hủy đơn hàng thành công!");
    return "redirect:/user/order-details";
}

    // ================================================
    // POST: User yêu cầu hoàn tiền / trả hàng (status 3 → 6)
    // Nếu VNPay: lưu thêm thông tin tài khoản ngân hàng
    // ================================================
    @PostMapping("/request-refund/{id}")
    public String requestRefund(
            @PathVariable Integer id,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "bankName",    required = false) String bankName,
            @RequestParam(value = "bankNumber",  required = false) String bankNumber,
            @RequestParam(value = "bankHolder",  required = false) String bankHolder,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
        Order order = orderRepository.findById(id).orElse(null);

        if (order == null || !order.getUser().getId().equals(currentUser.getId())) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
            return "redirect:/user/order-details";
        }

        if (order.getStatus() != 3) {
            ra.addFlashAttribute("errorMessage", "Chỉ có thể yêu cầu khi đơn hàng đang ở trạng thái 'Đã giao'!");
            return "redirect:/user/order-details";
        }

        // Kiểm tra thông tin ngân hàng bắt buộc nếu là VNPay
        boolean isVnpay = order.getPaymentMethod() != null && order.getPaymentMethod() == 1;
        if (isVnpay) {
            boolean missingBank = (bankName == null || bankName.isBlank())
                    || (bankNumber == null || bankNumber.isBlank())
                    || (bankHolder == null || bankHolder.isBlank());
            if (missingBank) {
                ra.addFlashAttribute("errorMessage", "Vui lòng nhập đầy đủ thông tin tài khoản ngân hàng để nhận hoàn tiền!");
                return "redirect:/user/order-details";
            }
            // Lưu thông tin ngân hàng
            String bankInfo = bankName.trim() + " | " + bankNumber.trim() + " | " + bankHolder.trim();
            order.setBankAccount(bankInfo);
        }

        // Ghi lý do vào ghi chú
        String refundType  = isVnpay ? "Yêu cầu hoàn tiền" : "Yêu cầu trả hàng";
        String reasonText  = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Không rõ";
        String noteAppend  = "[" + refundType + "] Lý do: " + reasonText;
        String currentNote = (order.getNote() != null && !order.getNote().isBlank())
                ? order.getNote() + " | " + noteAppend
                : noteAppend;

        order.setNote(currentNote);
        order.setStatus(6);
        orderRepository.save(order);

        String successMsg = isVnpay
                ? "Đã gửi yêu cầu hoàn tiền cho đơn #" + id + ". Chúng tôi sẽ liên hệ lại trong 24 giờ!"
                : "Đã gửi yêu cầu trả hàng cho đơn #" + id + ". Chúng tôi sẽ liên hệ lại trong 24 giờ!";
        ra.addFlashAttribute("successMessage", successMsg);
        return "redirect:/user/order-details";
    }
}