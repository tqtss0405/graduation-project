package com.poly.graduation_project.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.repository.OrderRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.service.OrderEmailService;
import com.poly.graduation_project.service.SessionService;
import com.poly.graduation_project.model.User;
import jakarta.transaction.Transactional;

@Controller
@RequestMapping("/admin/orders")
public class OrderController {

    private static final int PAGE_SIZE = 10;

    private static final Map<Integer, Integer> STATUS_PRIORITY = Map.of(
            0, 1,
            6, 2,
            1, 3,
            2, 4,
            3, 5,
            4, 6,
            7, 7,
            5, 8);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private OrderEmailService orderEmailService;

    // ================================================
    // GET: Danh sách đơn hàng
    // ================================================
    @GetMapping
    public String listOrders(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        User currentUser = (User) sessionService.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);

        List<Order> orders = (status != null)
                ? orderRepository.findByStatusOrderByCreateAtDesc(status)
                : orderRepository.findAllByOrderByCreateAtDesc();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            orders = orders.stream()
                    .filter(o -> o.getUser() != null && ((o.getUser().getFullname() != null
                            && o.getUser().getFullname().toLowerCase().contains(kw)) ||
                            (o.getUser().getEmail() != null && o.getUser().getEmail().toLowerCase().contains(kw))))
                    .collect(Collectors.toList());
        }

        if (fromDate != null && !fromDate.isEmpty()) {
            LocalDateTime from = LocalDate.parse(fromDate).atStartOfDay();
            orders = orders.stream()
                    .filter(o -> o.getCreateAt() != null && !o.getCreateAt().isBefore(from))
                    .collect(Collectors.toList());
        }
        if (toDate != null && !toDate.isEmpty()) {
            LocalDateTime to = LocalDate.parse(toDate).atTime(23, 59, 59);
            orders = orders.stream()
                    .filter(o -> o.getCreateAt() != null && !o.getCreateAt().isAfter(to))
                    .collect(Collectors.toList());
        }

        if (status == null) {
            orders = orders.stream()
                    .sorted(Comparator
                            .comparingInt((Order o) -> STATUS_PRIORITY.getOrDefault(o.getStatus(), 99))
                            .thenComparing(Comparator.comparing(Order::getCreateAt).reversed()))
                    .collect(Collectors.toList());
        }

        int totalItems = orders.size();
        int totalPages = (int) Math.ceil((double) totalItems / PAGE_SIZE);
        if (page < 1)
            page = 1;
        if (page > totalPages && totalPages > 0)
            page = totalPages;

        int fromIdx = (page - 1) * PAGE_SIZE;
        int toIdx = Math.min(fromIdx + PAGE_SIZE, totalItems);
        List<Order> pagedOrders = (totalItems > 0) ? orders.subList(fromIdx, toIdx) : orders;

        model.addAttribute("orders", pagedOrders);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);

        model.addAttribute("countAll", orderRepository.count());
        model.addAttribute("countPending", orderRepository.findByStatusOrderByCreateAtDesc(0).size());
        model.addAttribute("countConfirmed", orderRepository.findByStatusOrderByCreateAtDesc(1).size());
        model.addAttribute("countShipping", orderRepository.findByStatusOrderByCreateAtDesc(2).size());
        model.addAttribute("countDelivered", orderRepository.findByStatusOrderByCreateAtDesc(3).size());
        model.addAttribute("countCompleted", orderRepository.findByStatusOrderByCreateAtDesc(4).size());
        model.addAttribute("countCancelled", orderRepository.findByStatusOrderByCreateAtDesc(5).size());
        model.addAttribute("countRefund", orderRepository.findByStatusOrderByCreateAtDesc(6).size());
        model.addAttribute("countRefunded", orderRepository.findByStatusOrderByCreateAtDesc(7).size());

        return "admin-orders";
    }

    // ================================================
    // GET (AJAX): Lấy danh sách sản phẩm trong đơn
    // ================================================
    @GetMapping("/items/{id}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getOrderItems(@PathVariable Integer id) {

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getOrderDetails() == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, Object>> items = order.getOrderDetails().stream()
                .map(od -> {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("productName", od.getProduct() != null ? od.getProduct().getName() : "N/A");
                    item.put("author", od.getProduct() != null ? od.getProduct().getAuthor() : "");
                    item.put("quantity", od.getQuantity());
                    item.put("price", od.getPrice());
                    return item;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    // ================================================
    // POST: Cập nhật trạng thái thông thường
    // ================================================
    @PostMapping("/update-status/{id}")
    @Transactional
    public String updateStatus(
            @PathVariable Integer id,
            @RequestParam Integer status,
            RedirectAttributes ra) {

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
            return "redirect:/admin/orders";
        }

        int cur = order.getStatus();

        if (cur == 4) {
            ra.addFlashAttribute("errorMessage", "Không thể thay đổi đơn hàng đã hoàn thành!");
            return "redirect:/admin/orders";
        }
        if (cur == 7) {
            ra.addFlashAttribute("errorMessage", "Không thể thay đổi đơn hàng đã hoàn tiền!");
            return "redirect:/admin/orders";
        }
        if (cur == 5) {
            ra.addFlashAttribute("errorMessage", "Không thể thay đổi đơn hàng đã hủy!");
            return "redirect:/admin/orders";
        }

        if (status == 5) {
            if (cur == 2) {
                ra.addFlashAttribute("errorMessage", "Không thể hủy đơn đang giao!");
                return "redirect:/admin/orders";
            }
            if (cur == 3) {
                ra.addFlashAttribute("errorMessage", "Không thể hủy đơn đã giao!");
                return "redirect:/admin/orders";
            }
        }

        if (cur == 6 && status != 7 && status != 4) {
            ra.addFlashAttribute("errorMessage",
                    "Đơn yêu cầu hoàn tiền chỉ có thể 'Hoàn tiền thành công' hoặc 'Từ chối'!");
            return "redirect:/admin/orders";
        }

        order.setStatus(status);
        orderRepository.save(order);

        String msg = (cur == 6 && status == 4)
                ? "Đã từ chối hoàn tiền đơn #" + id + ". Đơn chuyển về Hoàn thành."
                : "Cập nhật trạng thái đơn #" + id + " thành công!";

        ra.addFlashAttribute("successMessage", msg);
        return "redirect:/admin/orders";
    }

    // ================================================
    // POST: Admin hủy đơn có lý do + hoàn kho + gửi mail
    // Chỉ áp dụng cho status 0 (Chờ xác nhận) và 1 (Đã xác nhận)
    // ================================================
    @PostMapping("/cancel/{id}")
    @Transactional
    public String adminCancelOrder(
            @PathVariable Integer id,
            @RequestParam(value = "cancelReason", required = false) String cancelReason,
            RedirectAttributes ra) {

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng #" + id + "!");
            return "redirect:/admin/orders";
        }

        int cur = order.getStatus();

        // Chỉ cho phép hủy khi đơn chưa giao (status 0 hoặc 1)
        if (cur != 0 && cur != 1) {
            ra.addFlashAttribute("errorMessage",
                    "Không thể hủy đơn #" + id + "! Chỉ hủy được đơn 'Chờ xác nhận' hoặc 'Đã xác nhận'.");
            return "redirect:/admin/orders";
        }

        // Hoàn lại số lượng tồn kho
        if (order.getOrderDetails() != null) {
            for (OrderDetail od : order.getOrderDetails()) {
                if (od.getProduct() != null && od.getQuantity() != null) {
                    var product = od.getProduct();
                    int newStock = (product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                            + od.getQuantity();
                    product.setStockQuantity(newStock);
                    productRepository.save(product);
                }
            }
        }

        // Ghi lý do vào ghi chú đơn hàng
        String reasonText = (cancelReason != null && !cancelReason.trim().isEmpty())
                ? cancelReason.trim()
                : "Không có lý do cụ thể";
        String cancelNote = "[ADMIN HUY] Ly do: " + reasonText;
        String currentNote = (order.getNote() != null && !order.getNote().isBlank())
                ? order.getNote() + " | " + cancelNote
                : cancelNote;
        order.setNote(currentNote);

        order.setStatus(5);
        // Sau khi set order.setStatus(5), thêm đoạn này:
        // Nếu đã thanh toán VNPay → ghi chú cần hoàn tiền thủ công
        if (order.getPaymentMethod() != null && order.getPaymentMethod() == 1
                && order.getPaymentStatus() != null && order.getPaymentStatus() == 1) {
            String refundNote = "[CẦN HOÀN TIỀN VNPAY] Vào VNPay Merchant Portal để hoàn tiền thủ công.";
            currentNote = currentNote + " | " + refundNote;
            order.setNote(currentNote);
        }
        orderRepository.save(order);

        // Gửi email thông báo cho khách
        if (order.getUser() != null) {
            orderEmailService.sendCancellationEmail(order.getUser(), order, reasonText);
        }

        ra.addFlashAttribute("successMessage",
                "Đã hủy đơn #" + id + " thành công! Email thông báo đã được gửi đến khách hàng.");
        return "redirect:/admin/orders";
    }
}