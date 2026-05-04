package com.poly.graduation_project.controller;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.repository.*;
import com.poly.graduation_project.service.SessionService;
import com.poly.graduation_project.model.User;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    @Autowired
    private SessionService sessionService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;

    @GetMapping("/admin/dashboard")
    public String dashboard(
            @RequestParam(value = "period", defaultValue = "month") String period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = (User) sessionService.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);
        // ── VALIDATE: fromDate phải <= toDate ─────────────────────────────────
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            redirectAttributes.addFlashAttribute("dateError",
                    "Ngày bắt đầu (" + fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                            ") không được lớn hơn ngày kết thúc (" +
                            toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
            return "redirect:/admin/dashboard";
        }

        // ── Xác định khoảng thời gian ──────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDt;
        LocalDateTime endDt;

        if (fromDate != null && toDate != null) {
            startDt = fromDate.atStartOfDay();
            endDt = toDate.atTime(23, 59, 59);
            period = "custom";
        } else {
            startDt = switch (period) {
                case "day" -> now.toLocalDate().atStartOfDay();
                case "week" -> now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay();
                case "year" -> now.toLocalDate().withDayOfYear(1).atStartOfDay();
                default -> now.toLocalDate().withDayOfMonth(1).atStartOfDay(); // month
            };
            endDt = now;
        }

        // ── Lấy toàn bộ đơn hàng ──────────────────────────────────────────────
        List<Order> allOrders = orderRepository.findAllByOrderByCreateAtDesc();

        List<Order> periodOrders = allOrders.stream()
                .filter(o -> o.getCreateAt() != null
                        && !o.getCreateAt().isBefore(startDt)
                        && !o.getCreateAt().isAfter(endDt))
                .collect(Collectors.toList());

        // ── Kỳ trước ──────────────────────────────────────────────────────────
        Duration dur = Duration.between(startDt, endDt);
        LocalDateTime prevStart = startDt.minus(dur).minusSeconds(1);
        LocalDateTime prevEnd = startDt.minusSeconds(1);

        List<Order> prevOrders = allOrders.stream()
                .filter(o -> o.getCreateAt() != null
                        && !o.getCreateAt().isBefore(prevStart)
                        && !o.getCreateAt().isAfter(prevEnd))
                .collect(Collectors.toList());

        // ── KPI ───────────────────────────────────────────────────────────────
        BigDecimal revenue = calcRevenue(periodOrders);
        BigDecimal prevRevenue = calcRevenue(prevOrders);
        long orderCount = periodOrders.size();
        long prevOrderCount = prevOrders.size();

        long newCustomers = userRepository.findByRole(false).stream()
                .filter(u -> orderRepository.findByUserOrderByCreateAtDesc(u).stream()
                        .min(Comparator.comparing(Order::getCreateAt))
                        .map(o -> !o.getCreateAt().isBefore(startDt) && !o.getCreateAt().isAfter(endDt))
                        .orElse(false))
                .count();

        long prevNewCustomers = userRepository.findByRole(false).stream()
                .filter(u -> orderRepository.findByUserOrderByCreateAtDesc(u).stream()
                        .min(Comparator.comparing(Order::getCreateAt))
                        .map(o -> !o.getCreateAt().isBefore(prevStart) && !o.getCreateAt().isAfter(prevEnd))
                        .orElse(false))
                .count();

        long soldQty = periodOrders.stream()
                .filter(o -> o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .mapToLong(od -> od.getQuantity() != null ? od.getQuantity() : 0)
                .sum();

        long prevSoldQty = prevOrders.stream()
                .filter(o -> o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .mapToLong(od -> od.getQuantity() != null ? od.getQuantity() : 0)
                .sum();

        model.addAttribute("revenue", revenue);
        model.addAttribute("revenueChange", pct(revenue, prevRevenue));
        model.addAttribute("orderCount", orderCount);
        model.addAttribute("orderChange", pct(BigDecimal.valueOf(orderCount), BigDecimal.valueOf(prevOrderCount)));
        model.addAttribute("newCustomers", newCustomers);
        model.addAttribute("customerChange",
                pct(BigDecimal.valueOf(newCustomers), BigDecimal.valueOf(prevNewCustomers)));
        model.addAttribute("soldQty", soldQty);
        model.addAttribute("soldQtyChange", pct(BigDecimal.valueOf(soldQty), BigDecimal.valueOf(prevSoldQty)));

        // ── Biểu đồ ───────────────────────────────────────────────────────────
        List<String> chartLabels = new ArrayList<>();
        List<BigDecimal> chartRevenue = new ArrayList<>();
        List<Long> chartOrders = new ArrayList<>();

        if ("day".equals(period)) {
            for (int h = 0; h < 24; h++) {
                int hour = h;
                LocalDateTime hStart = startDt.plusHours(h);
                LocalDateTime hEnd = hStart.plusHours(1).minusSeconds(1);
                List<Order> bucket = filterBucket(periodOrders, hStart, hEnd);
                chartLabels.add(String.format("%02d:00", hour));
                chartRevenue.add(calcRevenue(bucket));
                chartOrders.add((long) bucket.size());
            }
        } else if ("week".equals(period)) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("E dd/MM", new Locale("vi"));
            for (int d = 0; d < 7; d++) {
                LocalDateTime dStart = startDt.plusDays(d);
                LocalDateTime dEnd = dStart.plusDays(1).minusSeconds(1);
                List<Order> bucket = filterBucket(periodOrders, dStart, dEnd);
                chartLabels.add(dStart.format(dtf));
                chartRevenue.add(calcRevenue(bucket));
                chartOrders.add((long) bucket.size());
            }
        } else if ("year".equals(period)) {
            for (int m = 1; m <= 12; m++) {
                LocalDateTime mStart = LocalDateTime.of(now.getYear(), m, 1, 0, 0);
                LocalDateTime mEnd = mStart.plusMonths(1).minusSeconds(1);
                List<Order> bucket = filterBucket(periodOrders, mStart, mEnd);
                chartLabels.add("Tháng " + m);
                chartRevenue.add(calcRevenue(bucket));
                chartOrders.add((long) bucket.size());
            }
        } else {
            // month / custom → theo từng ngày
            LocalDate cursor = startDt.toLocalDate();
            LocalDate endDate2 = endDt.toLocalDate();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");
            while (!cursor.isAfter(endDate2)) {
                LocalDateTime dStart = cursor.atStartOfDay();
                LocalDateTime dEnd = cursor.atTime(23, 59, 59);
                List<Order> bucket = filterBucket(periodOrders, dStart, dEnd);
                chartLabels.add(cursor.format(dtf));
                chartRevenue.add(calcRevenue(bucket));
                chartOrders.add((long) bucket.size());
                cursor = cursor.plusDays(1);
            }
        }

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartRevenue", chartRevenue);
        model.addAttribute("chartOrders", chartOrders);

        // ── Doughnut ──────────────────────────────────────────────────────────
        model.addAttribute("countCompleted", countByStatus(periodOrders, 4));
        model.addAttribute("countPending", countByStatus(periodOrders, 0));
        model.addAttribute("countShipping", countByStatus(periodOrders, 2));
        model.addAttribute("countCancelled", countByStatus(periodOrders, 5));

        // ── Top 5 sản phẩm ─────────────────────────────────────────────────
        Map<Integer, long[]> productStats = new LinkedHashMap<>();
        periodOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != 5 && o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .forEach(od -> {
                    if (od.getProduct() == null)
                        return;
                    int pid = od.getProduct().getId();
                    long qty = od.getQuantity() != null ? od.getQuantity() : 0;
                    long rev = od.getPrice() != null ? od.getPrice().multiply(BigDecimal.valueOf(qty)).longValue() : 0;
                    productStats.merge(pid, new long[] { qty, rev }, (a, b) -> new long[] { a[0] + b[0], a[1] + b[1] });
                });

        List<Map<String, Object>> topProducts = productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    Product p = productRepository.findById(e.getKey()).orElse(null);
                    m.put("name", p != null ? p.getName() : "N/A");
                    m.put("category", p != null && p.getCategory() != null ? p.getCategory().getName() : "");
                    m.put("sold", e.getValue()[0]);
                    m.put("revenue", e.getValue()[1]);
                    m.put("stock", p != null ? p.getStockQuantity() : 0);
                    Double avg = p != null ? reviewRepository.avgRatingByProductId(p.getId()) : null;
                    m.put("rating", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
                    return m;
                })
                .collect(Collectors.toList());

        model.addAttribute("topProducts", topProducts);

        // ── Top danh mục ───────────────────────────────────────────────────────
        Map<String, Long> catSales = new LinkedHashMap<>();
        periodOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != 5 && o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .filter(od -> od.getProduct() != null && od.getProduct().getCategory() != null)
                .forEach(od -> {
                    String cat = od.getProduct().getCategory().getName();
                    long qty = od.getQuantity() != null ? od.getQuantity() : 0;
                    catSales.merge(cat, qty, Long::sum);
                });

        List<Map.Entry<String, Long>> catEntries = catSales.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .collect(Collectors.toList());

        model.addAttribute("categoryLabels",
                catEntries.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        model.addAttribute("categoryData",
                catEntries.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        // ── Truyền lại params ─────────────────────────────────────────────────
        model.addAttribute("period", period);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        // ── Tổng hệ thống ─────────────────────────────────────────────────────
        model.addAttribute("totalOrders", orderRepository.count());
        model.addAttribute("totalCustomers", userRepository.findByRole(false).size());
        model.addAttribute("totalProducts", productRepository.count());

        return "admin-dashboard";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<Order> filterBucket(List<Order> orders, LocalDateTime start, LocalDateTime end) {
        return orders.stream()
                .filter(o -> !o.getCreateAt().isBefore(start) && !o.getCreateAt().isAfter(end))
                .collect(Collectors.toList());
    }

    private BigDecimal calcRevenue(List<Order> orders) {
    return orders.stream()
            .filter(o -> o.getStatus() != null && o.getStatus() == 4) // chỉ Hoàn thành
            .map(o -> {
                BigDecimal total = o.getTotal()       != null ? o.getTotal()       : BigDecimal.ZERO;
                BigDecimal ship  = o.getShippingFee() != null ? o.getShippingFee() : BigDecimal.ZERO;
                return total.subtract(ship); // = tiền hàng sau discount, bỏ ship
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}

    private long countByStatus(List<Order> orders, int status) {
        return orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == status)
                .count();
    }

    /** (cur - prev) / prev * 100 */
    private double pct(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0)
            return 0;
        return cur.subtract(prev)
                .divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}