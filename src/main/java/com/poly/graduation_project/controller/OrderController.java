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
import com.poly.graduation_project.repository.OrderRepository;

@Controller
@RequestMapping("/admin/orders")
public class OrderController {

    private static final int PAGE_SIZE = 10;

    // Thứ tự ưu tiên hiển thị theo status
    private static final Map<Integer, Integer> STATUS_PRIORITY = Map.of(
        0, 1,  // Chờ xác nhận — ưu tiên cao nhất
        6, 2,  // Yêu cầu hoàn tiền
        1, 3,  // Đã xác nhận
        2, 4,  // Đang giao
        3, 5,  // Đã giao
        4, 6,  // Hoàn thành
        5, 7   // Đã hủy
    );

    @Autowired
    private OrderRepository orderRepository;

    // ================================================
    // GET: Danh sách đơn hàng
    // ================================================
    @GetMapping
    public String listOrders(
            @RequestParam(value = "status",    required = false) Integer status,
            @RequestParam(value = "keyword",   required = false) String keyword,
            @RequestParam(value = "fromDate",  required = false) String fromDate,
            @RequestParam(value = "toDate",    required = false) String toDate,
            @RequestParam(value = "page",      defaultValue = "1") int page,
            Model model) {

        // 1. Lấy danh sách gốc
        List<Order> orders = (status != null)
                ? orderRepository.findByStatusOrderByCreateAtDesc(status)
                : orderRepository.findAllByOrderByCreateAtDesc();

        // 2. Lọc theo keyword (tên hoặc email khách)
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            orders = orders.stream()
                    .filter(o -> o.getUser() != null && (
                            (o.getUser().getFullname() != null && o.getUser().getFullname().toLowerCase().contains(kw)) ||
                            (o.getUser().getEmail()    != null && o.getUser().getEmail().toLowerCase().contains(kw))
                    ))
                    .collect(Collectors.toList());
        }

        // 3. Lọc theo khoảng ngày
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

        // 4. Sắp xếp ưu tiên (chỉ khi tab "Tất cả")
        if (status == null) {
            orders = orders.stream()
                    .sorted(Comparator
                            .comparingInt((Order o) -> STATUS_PRIORITY.getOrDefault(o.getStatus(), 99))
                            .thenComparing(Comparator.comparing(Order::getCreateAt).reversed()))
                    .collect(Collectors.toList());
        }

        // 5. Phân trang
        int totalItems = orders.size();
        int totalPages = (int) Math.ceil((double) totalItems / PAGE_SIZE);
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

        int fromIdx = (page - 1) * PAGE_SIZE;
        int toIdx   = Math.min(fromIdx + PAGE_SIZE, totalItems);
        List<Order> pagedOrders = (totalItems > 0) ? orders.subList(fromIdx, toIdx) : orders;

        // 6. Đưa vào model
        model.addAttribute("orders",         pagedOrders);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword",        keyword);
        model.addAttribute("fromDate",       fromDate);
        model.addAttribute("toDate",         toDate);
        model.addAttribute("currentPage",    page);
        model.addAttribute("totalPages",     totalPages);
        model.addAttribute("totalItems",     totalItems);

        model.addAttribute("countAll",       orderRepository.count());
        model.addAttribute("countPending",   orderRepository.findByStatusOrderByCreateAtDesc(0).size());
        model.addAttribute("countConfirmed", orderRepository.findByStatusOrderByCreateAtDesc(1).size());
        model.addAttribute("countShipping",  orderRepository.findByStatusOrderByCreateAtDesc(2).size());
        model.addAttribute("countDelivered", orderRepository.findByStatusOrderByCreateAtDesc(3).size());
        model.addAttribute("countCompleted", orderRepository.findByStatusOrderByCreateAtDesc(4).size());
        model.addAttribute("countCancelled", orderRepository.findByStatusOrderByCreateAtDesc(5).size());
        model.addAttribute("countRefund",    orderRepository.findByStatusOrderByCreateAtDesc(6).size());

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
                    item.put("productName", od.getProduct() != null ? od.getProduct().getName()   : "N/A");
                    item.put("author",      od.getProduct() != null ? od.getProduct().getAuthor() : "");
                    item.put("quantity",    od.getQuantity());
                    item.put("price",       od.getPrice());
                    return item;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    // ================================================
    // POST: Cập nhật trạng thái
    // ================================================
    @PostMapping("/update-status/{id}")
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
        if (status == 5) {
            if (cur == 2) { ra.addFlashAttribute("errorMessage", "Không thể hủy đơn đang giao!"); return "redirect:/admin/orders"; }
            if (cur == 3) { ra.addFlashAttribute("errorMessage", "Không thể hủy đơn đã giao!");   return "redirect:/admin/orders"; }
            if (cur == 5) { ra.addFlashAttribute("errorMessage", "Đơn đã bị hủy rồi!");            return "redirect:/admin/orders"; }
        }
        if (cur == 6 && status != 4 && status != 5) {
            ra.addFlashAttribute("errorMessage", "Đơn yêu cầu hoàn tiền chỉ có thể Hoàn thành hoặc Hủy!");
            return "redirect:/admin/orders";
        }

        order.setStatus(status);
        orderRepository.save(order);
        ra.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn #" + id + " thành công!");
        return "redirect:/admin/orders";
    }
}