package com.poly.graduation_project.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.repository.OrderRepository;

@Controller
@RequestMapping("/admin/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    // ================================================
    // GET: Danh sách đơn hàng (có lọc theo status)
    // ================================================
    @GetMapping
    public String listOrders(
            @RequestParam(value = "status", required = false) Integer status,
            Model model) {

        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatusOrderByCreateAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreateAtDesc();
        }

        // Đếm số lượng theo từng trạng thái
        model.addAttribute("orders", orders);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("countAll",       orderRepository.count());
        model.addAttribute("countPending",   orderRepository.findByStatusOrderByCreateAtDesc(0).size());
        model.addAttribute("countConfirmed", orderRepository.findByStatusOrderByCreateAtDesc(1).size());
        model.addAttribute("countShipping",  orderRepository.findByStatusOrderByCreateAtDesc(2).size());
        model.addAttribute("countDelivered", orderRepository.findByStatusOrderByCreateAtDesc(3).size());
        model.addAttribute("countCompleted", orderRepository.findByStatusOrderByCreateAtDesc(4).size());
        model.addAttribute("countCancelled", orderRepository.findByStatusOrderByCreateAtDesc(5).size());
        return "admin-orders";
    }

    // ================================================
    // POST: Cập nhật trạng thái đơn hàng
    // ================================================
    @PostMapping("/update-status/{id}")
public String updateStatus(@PathVariable Integer id,
                           @RequestParam Integer status,
                           RedirectAttributes ra) {

    Order order = orderRepository.findById(id).orElse(null);
    if (order == null) {
        ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
        return "redirect:/admin/orders";
    }

    int currentStatus = order.getStatus();
    if (currentStatus == 4) {
        ra.addFlashAttribute("errorMessage", "Không thể thay đổi trạng thái đơn hàng đã hoàn thành!");
        return "redirect:/admin/orders";
    }
    // ❌ Không cho hủy nếu đã hoàn thành, đang giao, hoặc đã giao
    if (status == 5) {
        if (currentStatus == 4) {
            ra.addFlashAttribute("errorMessage", "Không thể hủy đơn hàng đã hoàn thành!");
            return "redirect:/admin/orders";
        }
        if (currentStatus == 2) {
            ra.addFlashAttribute("errorMessage", "Không thể hủy đơn hàng đang giao!");
            return "redirect:/admin/orders";
        }
        if (currentStatus == 3) {
            ra.addFlashAttribute("errorMessage", "Không thể hủy đơn hàng đã giao đến khách!");
            return "redirect:/admin/orders";
        }
        if (currentStatus == 5) {
            ra.addFlashAttribute("errorMessage", "Đơn hàng đã bị hủy rồi!");
            return "redirect:/admin/orders";
        }
    }

    order.setStatus(status);
    orderRepository.save(order);

    ra.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn hàng #" + id + " thành công!");
    return "redirect:/admin/orders";
}
}