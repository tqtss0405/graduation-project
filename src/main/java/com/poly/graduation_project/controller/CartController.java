package com.poly.graduation_project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.CartDetailRepository;
import com.poly.graduation_project.repository.ProductRepository;

import jakarta.servlet.http.HttpSession;

@Controller
public class CartController {

    @Autowired private CartDetailRepository cartDetailRepository;
    @Autowired private ProductRepository productRepository;

    // ============================================================
    // GET: Trang giỏ hàng
    // ============================================================
    @GetMapping("/user/cart")
    public String cartPage(org.springframework.ui.Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        model.addAttribute("cartItems", cartItems);
         int totalQuantity = cartItems.stream()
            .mapToInt(CartDetail::getQuantity)
            .sum();
        // Tính tổng tiền hàng
        java.math.BigDecimal subtotal = cartItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal shippingFee = subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal("30000");

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shippingFee", shippingFee);
        model.addAttribute("total", subtotal.add(shippingFee));
        model.addAttribute("totalQuantity", totalQuantity);
        return "cart";
    }

    // ============================================================
    // POST: Thêm sản phẩm vào giỏ hàng
    // ============================================================
    @PostMapping("/user/cart/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCart(
            @RequestParam("productId") Integer productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            response.put("success", false);
            response.put("message", "Vui lòng đăng nhập để thêm vào giỏ hàng!");
            response.put("redirect", "/login/form");
            return ResponseEntity.ok(response);
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !Boolean.TRUE.equals(product.getActive())) {
            response.put("success", false);
            response.put("message", "Sản phẩm không tồn tại!");
            return ResponseEntity.ok(response);
        }

        // Kiểm tra tồn kho
        if (product.getStockQuantity() <= 0) {
            response.put("success", false);
            response.put("message", "Sản phẩm đã hết hàng!");
            return ResponseEntity.ok(response);
        }

        // Tìm item trong giỏ, nếu có thì cộng thêm số lượng
        CartDetail cartDetail = cartDetailRepository
                .findByUserAndProduct(currentUser, product)
                .orElse(null);

        if (cartDetail != null) {
            int newQty = cartDetail.getQuantity() + quantity;
            // Không vượt tồn kho
            newQty = Math.min(newQty, product.getStockQuantity());
            cartDetail.setQuantity(newQty);
        } else {
            cartDetail = new CartDetail();
            cartDetail.setUser(currentUser);
            cartDetail.setProduct(product);
            cartDetail.setQuantity(Math.min(quantity, product.getStockQuantity()));
        }
        cartDetailRepository.save(cartDetail);

        // Đếm tổng số lượng để cập nhật badge
        int totalQty = cartDetailRepository.countTotalQuantityByUser(currentUser);

        response.put("success", true);
        response.put("message", "Đã thêm \"" + product.getName() + "\" vào giỏ hàng!");
        response.put("cartCount", totalQty);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // POST: Cập nhật số lượng (AJAX)
    // ============================================================
    @PostMapping("/user/cart/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateQuantity(
            @RequestParam("cartDetailId") Integer cartDetailId,
            @RequestParam("quantity") Integer quantity,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            response.put("success", false);
            return ResponseEntity.ok(response);
        }

        CartDetail cartDetail = cartDetailRepository.findById(cartDetailId).orElse(null);
        if (cartDetail == null || !cartDetail.getUser().getId().equals(currentUser.getId())) {
            response.put("success", false);
            response.put("message", "Không tìm thấy sản phẩm trong giỏ hàng!");
            return ResponseEntity.ok(response);
        }

        int stock = cartDetail.getProduct().getStockQuantity();
        if (quantity < 1) quantity = 1;
        if (quantity > stock) quantity = stock;

        cartDetail.setQuantity(quantity);
        cartDetailRepository.save(cartDetail);

        // Tính lại giá của dòng này
        java.math.BigDecimal lineTotal = cartDetail.getProduct().getPrice()
                .multiply(java.math.BigDecimal.valueOf(quantity));

        // Tính lại tổng giỏ
        List<CartDetail> allItems = cartDetailRepository.findByUser(currentUser);
        java.math.BigDecimal subtotal = allItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal shippingFee = subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                ? java.math.BigDecimal.ZERO : new java.math.BigDecimal("30000");

        int totalQty = cartDetailRepository.countTotalQuantityByUser(currentUser);

        response.put("success", true);
        response.put("lineTotal", lineTotal);
        response.put("subtotal", subtotal);
        response.put("shippingFee", shippingFee);
        response.put("total", subtotal.add(shippingFee));
        response.put("cartCount", totalQty);
        response.put("quantity", quantity);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // POST: Xóa item khỏi giỏ hàng
    // ============================================================
    @PostMapping("/user/cart/remove/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeItem(
            @PathVariable Integer id,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            response.put("success", false);
            return ResponseEntity.ok(response);
        }

        CartDetail cartDetail = cartDetailRepository.findById(id).orElse(null);
        if (cartDetail != null && cartDetail.getUser().getId().equals(currentUser.getId())) {
            cartDetailRepository.delete(cartDetail);
        }

        List<CartDetail> allItems = cartDetailRepository.findByUser(currentUser);
        java.math.BigDecimal subtotal = allItems.stream()
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal shippingFee = subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                ? java.math.BigDecimal.ZERO : new java.math.BigDecimal("30000");

        int totalQty = cartDetailRepository.countTotalQuantityByUser(currentUser);

        response.put("success", true);
        response.put("subtotal", subtotal);
        response.put("shippingFee", shippingFee);
        response.put("total", subtotal.add(shippingFee));
        response.put("cartCount", totalQty);
        response.put("isEmpty", allItems.isEmpty());
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // GET (AJAX): Đếm số lượng trong giỏ (dùng để cập nhật badge navbar)
    // ============================================================
    @GetMapping("/user/cart/count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCartCount(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");
        int count = 0;
        if (currentUser != null) {
            count = cartDetailRepository.countTotalQuantityByUser(currentUser);
        }
        response.put("count", count);
        return ResponseEntity.ok(response);
    }
}