package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.OrderRepository;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user/order-details")
public class UserOrderController {

    @Autowired
    private OrderRepository orderRepository;

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
    // POST: User hủy đơn (chỉ hủy được khi status = 0)
    // ================================================
    @PostMapping("/cancel/{id}")
public String cancelOrder(
        @PathVariable Integer id,
        HttpSession session,
        RedirectAttributes ra) {

    User currentUser = (User) session.getAttribute("currentUser");

    Order order = orderRepository.findById(id).orElse(null);

    if (order == null || !order.getUser().getId().equals(currentUser.getId())) {
        ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
        return "redirect:/user/order-details";
    }

    // ✅ Cho hủy khi chưa giao (status 0 hoặc 1)
    // ❌ Không cho hủy khi đang giao, đã giao, hoàn thành, đã hủy
    if (order.getStatus() >= 2) {
        ra.addFlashAttribute("errorMessage", "Không thể hủy đơn hàng khi hàng đã được giao đi!");
        return "redirect:/user/order-details";
    }

    order.setStatus(5);
    orderRepository.save(order);

    ra.addFlashAttribute("successMessage", "Đã hủy đơn hàng #" + id + " thành công!");
    return "redirect:/user/order-details";
}
}