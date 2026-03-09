package com.poly.graduation_project.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Address;
import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.model.Voucher;
import com.poly.graduation_project.repository.AddressRepository;
import com.poly.graduation_project.repository.CartDetailRepository;
import com.poly.graduation_project.repository.OrderDetailRepository;
import com.poly.graduation_project.repository.OrderRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.repository.VoucherRepository;
import com.poly.graduation_project.util.VNPayUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller 
public class CheckoutController {

    private static final BigDecimal FREE_SHIP_THRESHOLD = new BigDecimal("150000");
    private static final BigDecimal SHIP_FEE = new BigDecimal("30000");

    @Autowired private CartDetailRepository cartDetailRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderDetailRepository orderDetailRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private VoucherRepository voucherRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private VNPayUtil vnPayUtil;
    @Autowired private JavaMailSender mailSender;

    // ============================================================
    // GET: Trang Checkout
    // ============================================================
    @GetMapping("/user/checkout")
    public String checkoutPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty()) return "redirect:/user/cart";
        int totalQuantity = cartItems.stream()
        .mapToInt(CartDetail::getQuantity)
        .sum();
model.addAttribute("totalQuantity", totalQuantity);
        // Tính tổng
        BigDecimal subtotal = calcSubtotal(cartItems);
        BigDecimal shippingFee = subtotal.compareTo(FREE_SHIP_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIP_FEE;

        // Địa chỉ đã có của user
        List<Address> addresses = addressRepository.findByUser(currentUser);
        Address defaultAddress = addressRepository
                .findByUserAndIsDefaultTrue(currentUser).orElse(null);

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", subtotal.add(shippingFee));
        model.addAttribute("addresses", addresses);
        model.addAttribute("defaultAddress", defaultAddress);
        model.addAttribute("currentUser", currentUser);
        return "checkout";
    }

    // ============================================================
    // POST (AJAX): Apply voucher
    // ============================================================
    @PostMapping("/user/checkout/apply-voucher")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyVoucher(
            @RequestParam("code") String code,
            HttpSession session) {

        Map<String, Object> res = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            res.put("success", false);
            res.put("message", "Vui lòng đăng nhập!");
            return ResponseEntity.ok(res);
        }

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        BigDecimal subtotal = calcSubtotal(cartItems);

        Voucher voucher = voucherRepository.findByCode(code.trim().toUpperCase()).orElse(null);
        if (voucher == null) {
            res.put("success", false);
            res.put("message", "Mã giảm giá không tồn tại!");
            return ResponseEntity.ok(res);
        }
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            res.put("success", false);
            res.put("message", "Mã giảm giá đã hết hiệu lực!");
            return ResponseEntity.ok(res);
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartedAt() != null && now.isBefore(voucher.getStartedAt())) {
            res.put("success", false);
            res.put("message", "Mã giảm giá chưa đến ngày sử dụng!");
            return ResponseEntity.ok(res);
        }
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt())) {
            res.put("success", false);
            res.put("message", "Mã giảm giá đã hết hạn!");
            return ResponseEntity.ok(res);
        }
        if (voucher.getQuantity() != null && voucher.getQuantity() <= 0) {
            res.put("success", false);
            res.put("message", "Mã giảm giá đã được sử dụng hết!");
            return ResponseEntity.ok(res);
        }

        // Tính tiền giảm
        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(voucher.getDiscount()))
                .divide(BigDecimal.valueOf(100));

        res.put("success", true);
        res.put("message", "Áp dụng mã \"" + voucher.getCode() + "\" thành công! Giảm " + voucher.getDiscount() + "%");
        res.put("discountPercent", voucher.getDiscount());
        res.put("discountAmount", discount);
        res.put("voucherId", voucher.getId());
        res.put("voucherName", voucher.getName());
        return ResponseEntity.ok(res);
    }

    // ============================================================
    // POST: Đặt hàng COD
    // ============================================================
    @Transactional
    @PostMapping("/user/checkout/cod")
    public String placeCodOrder(
            @RequestParam("addressText") String addressText,
            @RequestParam(value = "voucherId", required = false) Integer voucherId,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
       
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty()) return "redirect:/user/cart";

        try {
            Order order = buildOrder(currentUser, cartItems, addressText, voucherId, note, 0); // 0 = COD
            order.setPaymentStatus(0); // Chưa thanh toán (COD)
            order.setStatus(0);        // Chờ xác nhận
            Order savedOrder = orderRepository.save(order);

            saveOrderDetails(savedOrder, cartItems);
            clearCartAndStock(currentUser, cartItems);
            sendConfirmationEmail(currentUser, savedOrder);

            session.setAttribute("lastOrderId", savedOrder.getId());
            return "redirect:/user/order-success";

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Đặt hàng thất bại: " + e.getMessage());
            return "redirect:/user/checkout";
        }
    }

    // ============================================================
    // POST: Đặt hàng VNPay → Redirect đến VNPay
    // ============================================================
    @PostMapping("/user/checkout/vnpay")
    public String initiateVNPayPayment(
            @RequestParam("addressText") String addressText,
            @RequestParam(value = "voucherId", required = false) Integer voucherId,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            HttpServletRequest request) {

        User currentUser = (User) session.getAttribute("currentUser");
 

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty()) return "redirect:/user/cart";

        // Lưu thông tin checkout vào session để dùng sau khi VNPay callback
        session.setAttribute("vnpay_addressText", addressText);
        session.setAttribute("vnpay_voucherId", voucherId);
        session.setAttribute("vnpay_note", note);

        // Tính tổng tiền
        BigDecimal subtotal = calcSubtotal(cartItems);
        BigDecimal shippingFee = subtotal.compareTo(FREE_SHIP_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIP_FEE;
        BigDecimal discount = BigDecimal.ZERO;
        if (voucherId != null) {
            Voucher v = voucherRepository.findById(voucherId).orElse(null);
            if (v != null) {
                discount = subtotal.multiply(BigDecimal.valueOf(v.getDiscount()))
                        .divide(BigDecimal.valueOf(100));
            }
        }
        BigDecimal total = subtotal.add(shippingFee).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        // Tạo mã giao dịch tạm (timestamp)
        String txnRef = String.valueOf(System.currentTimeMillis());
        session.setAttribute("vnpay_txnRef", txnRef);

        String ipAddr = vnPayUtil.getIpAddress(request);
        String orderInfo = "Thanh toan don hang BEOBOOKS - " + currentUser.getEmail();
        String paymentUrl = vnPayUtil.createPaymentUrl(txnRef, total.longValue(), orderInfo, ipAddr);

        return "redirect:" + paymentUrl;
    }

    // ============================================================
    // GET: VNPay callback sau khi thanh toán
    // ============================================================
    @Transactional
    @GetMapping("/vnpay/return")
    public String vnpayReturn(
            @RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes ra) {

        // Xác minh chữ ký
        if (!vnPayUtil.verifySignature(params)) {
            ra.addFlashAttribute("paymentError", "Chữ ký không hợp lệ!");
            return "redirect:/user/checkout";
        }

        String responseCode = params.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            ra.addFlashAttribute("paymentError", "Thanh toán VNPay thất bại! (Mã: " + responseCode + ")");
            return "redirect:/user/checkout";
        }

        // Thanh toán thành công → tạo đơn hàng
        User currentUser = (User) session.getAttribute("currentUser");

        String addressText = (String) session.getAttribute("vnpay_addressText");
        Integer voucherId = (Integer) session.getAttribute("vnpay_voucherId");
        String note = (String) session.getAttribute("vnpay_note");

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty()) return "redirect:/home";

        try {
            Order order = buildOrder(currentUser, cartItems, addressText, voucherId, note, 1); // 1 = VNPay
            order.setPaymentStatus(1); // Đã thanh toán
            order.setStatus(1);        // Đã xác nhận (vì đã thanh toán)
            Order savedOrder = orderRepository.save(order);

            saveOrderDetails(savedOrder, cartItems);
            clearCartAndStock(currentUser, cartItems);
            sendConfirmationEmail(currentUser, savedOrder);

            // Xóa session vnpay
            session.removeAttribute("vnpay_addressText");
            session.removeAttribute("vnpay_voucherId");
            session.removeAttribute("vnpay_note");
            session.removeAttribute("vnpay_txnRef");
            session.setAttribute("lastOrderId", savedOrder.getId());

            return "redirect:/user/order-success";

        } catch (Exception e) {
            ra.addFlashAttribute("paymentError", "Lỗi lưu đơn hàng: " + e.getMessage());
            return "redirect:/user/checkout";
        }
    }

    // ============================================================
    // GET: Trang order-success
    // ============================================================
    @GetMapping("/user/order-success")
    public String orderSuccess(Model model, HttpSession session) {
        Integer lastOrderId = (Integer) session.getAttribute("lastOrderId");
        if (lastOrderId == null) return "redirect:/home";

        Order order = orderRepository.findById(lastOrderId).orElse(null);
        model.addAttribute("order", order);
        session.removeAttribute("lastOrderId");
        return "order-success";
    }

    // ============================================================
    // Helper: Xây dựng Order entity
    // ============================================================
    private Order buildOrder(User user, List<CartDetail> cartItems,
                              String addressText, Integer voucherId,
                              String note, int paymentMethod) {
        BigDecimal subtotal = calcSubtotal(cartItems);
        BigDecimal shippingFee = subtotal.compareTo(FREE_SHIP_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIP_FEE;

        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        if (voucherId != null) {
            voucher = voucherRepository.findById(voucherId).orElse(null);
            if (voucher != null) {
                discount = subtotal.multiply(BigDecimal.valueOf(voucher.getDiscount()))
                        .divide(BigDecimal.valueOf(100));
                // Trừ số lượng voucher
                if (voucher.getQuantity() != null && voucher.getQuantity() > 0) {
                    voucher.setQuantity(voucher.getQuantity() - 1);
                    voucherRepository.save(voucher);
                }
            }
        }

        BigDecimal total = subtotal.add(shippingFee).subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        Order order = new Order();
        order.setUser(user);
        order.setAddress(addressText);
        order.setTotal(total);
        order.setFreeShip(shippingFee.compareTo(BigDecimal.ZERO) == 0);
        order.setCreateAt(LocalDateTime.now());
        order.setPaymentMethod(paymentMethod);
        order.setNote(note);
        order.setTotalDiscount(discount);
        order.setVoucher(voucher);
        return order;
    }

    // Helper: Lưu OrderDetail + trừ kho
    private void saveOrderDetails(Order order, List<CartDetail> cartItems) {
        for (CartDetail ci : cartItems) {
            OrderDetail od = new OrderDetail();
            od.setOrder(order);
            od.setProduct(ci.getProduct());
            od.setQuantity(ci.getQuantity());
            od.setPrice(ci.getProduct().getPrice());
            orderDetailRepository.save(od);
        }
    }
    @Transactional
    // Helper: Trừ tồn kho và xóa giỏ hàng
    private void clearCartAndStock(User user, List<CartDetail> cartItems) {
        for (CartDetail ci : cartItems) {
            var product = ci.getProduct();
            int newStock = Math.max(0, product.getStockQuantity() - ci.getQuantity());
            product.setStockQuantity(newStock);
            productRepository.save(product);
        }
        cartDetailRepository.deleteByUser(user);
    }

    // Helper: Tính subtotal
    private BigDecimal calcSubtotal(List<CartDetail> cartItems) {
        return cartItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Helper: Gửi email xác nhận
    private void sendConfirmationEmail(User user, Order order) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(user.getEmail());
            msg.setSubject("🎉 BEOBOOKS - Xác nhận đơn hàng #" + order.getId());
            msg.setText(
                "Xin chào " + user.getFullname() + ",\n\n" +
                "Đơn hàng #" + order.getId() + " của bạn đã được đặt thành công!\n\n" +
                "📦 Địa chỉ giao hàng: " + order.getAddress() + "\n" +
                "💰 Tổng thanh toán: " + String.format("%,.0f", order.getTotal()) + "đ\n" +
                "💳 Phương thức: " + (order.getPaymentMethod() == 1 ? "VNPay (Đã thanh toán)" : "Thanh toán khi nhận hàng (COD)") + "\n\n" +
                "Cảm ơn bạn đã mua sắm tại BEOBOOKS! ❤️\n" +
                "Chúng tôi sẽ liên hệ xác nhận và giao hàng sớm nhất.\n\n" +
                "📚 BEOBOOKS - Nơi lan tỏa văn hóa đọc"
            );
            mailSender.send(msg);
        } catch (Exception e) {
            // Không để lỗi email ảnh hưởng đến flow đặt hàng
            System.err.println("Lỗi gửi email xác nhận: " + e.getMessage());
        }
    }
}