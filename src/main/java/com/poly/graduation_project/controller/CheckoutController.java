package com.poly.graduation_project.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
import com.poly.graduation_project.service.OrderEmailService;
import com.poly.graduation_project.util.VNPayUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

@Controller
public class CheckoutController {

    private static final BigDecimal DEFAULT_SHIP_FEE = new BigDecimal("30000");

    @Autowired
    private CartDetailRepository cartDetailRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private VNPayUtil vnPayUtil;
    @Autowired
    private OrderEmailService orderEmailService;

    // ============================================================
    // GET: Trang Checkout
    // ============================================================
    @GetMapping("/user/checkout")
    public String checkoutPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty())
            return "redirect:/user/cart";

        List<CartDetail> validItems = cartItems.stream()
                .filter(ci -> Boolean.TRUE.equals(ci.getProduct().getActive())
                        && ci.getProduct().getStockQuantity() > 0)
                .collect(Collectors.toList());

        if (validItems.isEmpty())
            return "redirect:/user/cart";

        for (CartDetail ci : validItems) {
            Long stock = ci.getProduct().getStockQuantity();
            if (ci.getQuantity() > stock) {
                ci.setQuantity(stock);
                cartDetailRepository.save(ci);
            }
        }

        Long totalQuantity = cartItems.stream().mapToLong(CartDetail::getQuantity).sum();
        BigDecimal subtotal = calcSubtotal(validItems);

        List<Address> addresses = addressRepository.findByUser(currentUser);
        Address defaultAddress = addressRepository.findByUserAndIsDefaultTrue(currentUser).orElse(null);

        model.addAttribute("cartItems", validItems);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shippingFee", BigDecimal.ZERO);
        model.addAttribute("total", subtotal);
        model.addAttribute("addresses", addresses);
        model.addAttribute("defaultAddress", defaultAddress);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("totalQuantity", totalQuantity);
        return "checkout";
    }

    // ============================================================
    // POST (AJAX): Apply voucher — kiểm tra user chưa dùng voucher này
    // ============================================================
    @PostMapping("/user/checkout/apply-voucher")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyVoucher(
            @RequestParam("code") String code,
            @RequestParam(value = "shippingFee", required = false, defaultValue = "0") BigDecimal shippingFee,
            HttpSession session) {

        Map<String, Object> res = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        BigDecimal subtotal = calcSubtotal(cartItems);
        BigDecimal totalBeforeDiscount = subtotal.add(shippingFee);

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

        // ── Kiểm tra user đã dùng voucher này chưa ──────────────────────────
        if (currentUser != null &&
                orderRepository.existsByUserAndVoucherId(currentUser, voucher.getId())) {
            res.put("success", false);
            res.put("message", "Bạn đã sử dụng mã giảm giá này rồi!");
            return ResponseEntity.ok(res);
        }

        BigDecimal discount = totalBeforeDiscount
                .multiply(BigDecimal.valueOf(voucher.getDiscount()))
                .divide(BigDecimal.valueOf(100));

        BigDecimal finalTotal = totalBeforeDiscount.subtract(discount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0)
            finalTotal = BigDecimal.ZERO;

        res.put("success", true);
        res.put("message", "Áp dụng mã \"" + voucher.getCode() + "\" thành công! Giảm " + voucher.getDiscount() + "%");
        res.put("discountPercent", voucher.getDiscount());
        res.put("discountAmount", discount);
        res.put("total", finalTotal);
        res.put("shippingFee", shippingFee);
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
            @RequestParam(value = "shippingFee", required = false, defaultValue = "30000") BigDecimal shippingFee,
            HttpSession session, RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> allItems = cartDetailRepository.findByUser(currentUser);

        List<CartDetail> cartItems = allItems.stream()
                .filter(ci -> Boolean.TRUE.equals(ci.getProduct().getActive())
                        && ci.getProduct().getStockQuantity() > 0)
                .collect(Collectors.toList());

        if (cartItems.isEmpty())
            return "redirect:/user/cart";

        // Validate voucher lần cuối trước khi lưu đơn
        if (voucherId != null) {
            String voucherError = validateVoucherForUser(currentUser, voucherId);
            if (voucherError != null) {
                ra.addFlashAttribute("errorMessage", voucherError);
                return "redirect:/user/checkout";
            }
        }

        try {
            Order order = buildOrder(currentUser, cartItems, addressText, voucherId, note, 0, shippingFee);
            order.setPaymentStatus(0);
            order.setStatus(0);
            Order savedOrder = orderRepository.save(order);
            saveOrderDetails(savedOrder, cartItems);
            clearCartAndStock(currentUser, cartItems, allItems);
            orderEmailService.sendConfirmationEmail(currentUser, savedOrder);
            session.setAttribute("lastOrderId", savedOrder.getId());
            return "redirect:/user/order-success";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Đặt hàng thất bại: " + e.getMessage());
            return "redirect:/user/checkout";
        }
    }

    // ============================================================
    // POST: Đặt hàng VNPay
    // ============================================================
    @PostMapping("/user/checkout/vnpay")
    public String initiateVNPayPayment(
            @RequestParam("addressText") String addressText,
            @RequestParam(value = "voucherId", required = false) Integer voucherId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "shippingFee", required = false, defaultValue = "30000") BigDecimal shippingFee,
            HttpSession session, HttpServletRequest request) {

        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        if (cartItems.isEmpty())
            return "redirect:/user/cart";

        // Validate voucher trước khi redirect sang VNPay
        if (voucherId != null) {
            String voucherError = validateVoucherForUser(currentUser, voucherId);
            if (voucherError != null) {
                // Không thể dùng RedirectAttributes ở đây nên lưu session tạm
                session.setAttribute("checkoutError", voucherError);
                return "redirect:/user/checkout";
            }
        }

        session.setAttribute("vnpay_addressText", addressText);
        session.setAttribute("vnpay_voucherId", voucherId);
        session.setAttribute("vnpay_note", note);
        session.setAttribute("vnpay_shippingFee", shippingFee);

        BigDecimal subtotal = calcSubtotal(cartItems);
        BigDecimal totalBeforeDiscount = subtotal.add(shippingFee);

        BigDecimal discount = BigDecimal.ZERO;
        if (voucherId != null) {
            Voucher v = voucherRepository.findById(voucherId).orElse(null);
            if (v != null) {
                discount = totalBeforeDiscount
                        .multiply(BigDecimal.valueOf(v.getDiscount()))
                        .divide(BigDecimal.valueOf(100));
            }
        }
        BigDecimal total = totalBeforeDiscount.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0)
            total = BigDecimal.ZERO;

        String txnRef = String.valueOf(System.currentTimeMillis());
        session.setAttribute("vnpay_txnRef", txnRef);

        String ipAddr = vnPayUtil.getIpAddress(request);
        String orderInfo = "Thanh toan don hang BEOBOOKS - " + currentUser.getEmail();
        String paymentUrl = vnPayUtil.createPaymentUrl(txnRef, total.longValue(), orderInfo, ipAddr);

        return "redirect:" + paymentUrl;
    }

    // ============================================================
    // GET: VNPay callback
    // ============================================================
    @Transactional
    @GetMapping("/vnpay/return")
    public String vnpayReturn(
            @RequestParam Map<String, String> params,
            HttpSession session, RedirectAttributes ra) {

        if (!vnPayUtil.verifySignature(params)) {
            ra.addFlashAttribute("paymentError", "Chữ ký không hợp lệ!");
            return "redirect:/user/checkout";
        }
        String responseCode = params.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            ra.addFlashAttribute("paymentError", "Thanh toán VNPay thất bại! (Mã: " + responseCode + ")");
            return "redirect:/user/checkout";
        }

        User currentUser = (User) session.getAttribute("currentUser");
        String addressText = (String) session.getAttribute("vnpay_addressText");
        Integer voucherId = (Integer) session.getAttribute("vnpay_voucherId");
        String note = (String) session.getAttribute("vnpay_note");
        BigDecimal shippingFee = (BigDecimal) session.getAttribute("vnpay_shippingFee");
        if (shippingFee == null)
            shippingFee = DEFAULT_SHIP_FEE;

        List<CartDetail> allItems = cartDetailRepository.findByUser(currentUser);
        List<CartDetail> cartItems = allItems.stream()
                .filter(ci -> Boolean.TRUE.equals(ci.getProduct().getActive())
                        && ci.getProduct().getStockQuantity() > 0)
                .collect(Collectors.toList());

        if (cartItems.isEmpty())
            return "redirect:/home";

        try {
            Order order = buildOrder(currentUser, cartItems, addressText, voucherId, note, 1, shippingFee);
            order.setPaymentStatus(1);
            order.setStatus(1);
            Order savedOrder = orderRepository.save(order);
            saveOrderDetails(savedOrder, cartItems);
            clearCartAndStock(currentUser, cartItems, allItems);
            orderEmailService.sendConfirmationEmail(currentUser, savedOrder);

            session.removeAttribute("vnpay_addressText");
            session.removeAttribute("vnpay_voucherId");
            session.removeAttribute("vnpay_note");
            session.removeAttribute("vnpay_txnRef");
            session.removeAttribute("vnpay_shippingFee");
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
        if (lastOrderId == null)
            return "redirect:/home";
        Order order = orderRepository.findById(lastOrderId).orElse(null);
        model.addAttribute("order", order);
        session.removeAttribute("lastOrderId");
        return "order-success";
    }

    // ============================================================
    // Helper: Validate voucher cho user (kiểm tra đã dùng chưa)
    // Trả về null nếu hợp lệ, trả về message lỗi nếu không
    // ============================================================
    private String validateVoucherForUser(User user, Integer voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher == null)
            return "Mã giảm giá không tồn tại!";
        if (!Boolean.TRUE.equals(voucher.getActive()))
            return "Mã giảm giá đã hết hiệu lực!";

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getEndAt() != null && now.isAfter(voucher.getEndAt()))
            return "Mã giảm giá đã hết hạn!";
        if (voucher.getQuantity() != null && voucher.getQuantity() <= 0)
            return "Mã giảm giá đã hết lượt sử dụng!";
        if (user != null && orderRepository.existsByUserAndVoucherId(user, voucherId))
            return "Bạn đã sử dụng mã giảm giá này rồi!";

        return null; // hợp lệ
    }

    // ============================================================
    // Helper: Xây dựng Order entity
    // ============================================================
    private Order buildOrder(User user, List<CartDetail> cartItems,
            String addressText, Integer voucherId,
            String note, int paymentMethod, BigDecimal shippingFee) {

        BigDecimal subtotal = calcSubtotal(cartItems);
        if (shippingFee == null)
            shippingFee = DEFAULT_SHIP_FEE;

        BigDecimal totalBeforeDiscount = subtotal.add(shippingFee);
        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;

        if (voucherId != null) {
            voucher = voucherRepository.findById(voucherId).orElse(null);
            if (voucher != null) {
                discount = totalBeforeDiscount
                        .multiply(BigDecimal.valueOf(voucher.getDiscount()))
                        .divide(BigDecimal.valueOf(100));
                // Trừ số lượng voucher
                if (voucher.getQuantity() != null && voucher.getQuantity() > 0) {
                    voucher.setQuantity(voucher.getQuantity() - 1);
                    voucherRepository.save(voucher);
                }
            }
        }

        BigDecimal total = totalBeforeDiscount.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0)
            total = BigDecimal.ZERO;

        Order order = new Order();
        order.setUser(user);
        order.setAddress(addressText);
        order.setTotal(total);
        order.setShippingFee(shippingFee);
        order.setCreateAt(LocalDateTime.now());
        order.setPaymentMethod(paymentMethod);
        order.setNote(note);
        order.setTotalDiscount(discount);
        order.setVoucher(voucher);
        return order;
    }

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
    private void clearCartAndStock(User user, List<CartDetail> validItems, List<CartDetail> allItems) {
        for (CartDetail ci : validItems) {
            var product = ci.getProduct();
            Long newStock = Math.max(0, product.getStockQuantity() - ci.getQuantity());
            product.setStockQuantity(newStock);
            productRepository.save(product);
        } 
        for (CartDetail ci : validItems) {
            cartDetailRepository.deleteById(ci.getId());
        }
    }

    private BigDecimal calcSubtotal(List<CartDetail> cartItems) {
        return cartItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}