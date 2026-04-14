package com.poly.graduation_project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private CartDetailRepository cartDetailRepository;
    @Autowired
    private ProductRepository productRepository;

    // ============================================================
    // Helper: Kiểm tra sản phẩm có thể mua được không
    // (active + danh mục active + còn hàng)
    // ============================================================
    private boolean isAvailable(Product p) {
        if (p == null) return false;
        if (!Boolean.TRUE.equals(p.getActive())) return false;
        if (p.getStockQuantity() == null || p.getStockQuantity() <= 0) return false;
        // Nếu danh mục bị ẩn → coi như không thể mua
        if (p.getCategory() != null && !Boolean.TRUE.equals(p.getCategory().getActive())) return false;
        return true;
    }

    // ============================================================
    // GET: Trang giỏ hàng
    // ============================================================
    @GetMapping("/user/cart")
    public String cartPage(org.springframework.ui.Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);

        // Sắp xếp: có thể mua lên trên, không thể mua xuống dưới
        List<CartDetail> sortedItems = cartItems.stream()
                .sorted((a, b) -> {
                    boolean aOk = isAvailable(a.getProduct());
                    boolean bOk = isAvailable(b.getProduct());
                    return Boolean.compare(bOk, aOk);
                })
                .collect(Collectors.toList());

        // Có ít nhất 1 sản phẩm có thể mua
        boolean hasActiveItems = sortedItems.stream()
                .anyMatch(ci -> isAvailable(ci.getProduct()));

        // Có ít nhất 1 sản phẩm không thể mua (hết hàng / ẩn / danh mục ẩn)
        boolean hasOutOfStock = sortedItems.stream()
                .anyMatch(ci -> !isAvailable(ci.getProduct())
                        || (ci.getProduct() != null
                            && ci.getProduct().getStockQuantity() != null
                            && ci.getProduct().getStockQuantity() < ci.getQuantity()));

        // Chỉ tính tiền cho sản phẩm có thể mua
        java.math.BigDecimal subtotal = sortedItems.stream()
                .filter(ci -> isAvailable(ci.getProduct()))
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal shippingFee = subtotal.compareTo(java.math.BigDecimal.ZERO) == 0
                ? java.math.BigDecimal.ZERO
                : subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                        ? java.math.BigDecimal.ZERO
                        : new java.math.BigDecimal("30000");

        Long totalQuantity = sortedItems.stream().mapToLong(CartDetail::getQuantity).sum();

        model.addAttribute("cartItems",      sortedItems);
        model.addAttribute("hasActiveItems", hasActiveItems);
        model.addAttribute("hasOutOfStock",  hasOutOfStock);
        model.addAttribute("subtotal",       subtotal);
        model.addAttribute("shippingFee",    shippingFee);
        model.addAttribute("total",          subtotal.add(shippingFee));
        model.addAttribute("totalQuantity",  totalQuantity);
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

        // Kiểm tra sản phẩm có thể mua không (bao gồm cả danh mục)
        if (!isAvailable(product)) {
            String msg = "Sản phẩm không thể mua lúc này!";
            if (product != null) {
                if (!Boolean.TRUE.equals(product.getActive())) {
                    msg = "Sản phẩm không tồn tại!";
                } else if (product.getCategory() != null && !Boolean.TRUE.equals(product.getCategory().getActive())) {
                    msg = "Sản phẩm thuộc danh mục đã ngừng kinh doanh!";
                } else {
                    msg = "Sản phẩm đã hết hàng!";
                }
            }
            response.put("success", false);
            response.put("message", msg);
            return ResponseEntity.ok(response);
        }

        // Tìm item trong giỏ, nếu có thì cộng thêm số lượng
        CartDetail cartDetail = cartDetailRepository
                .findByUserAndProduct(currentUser, product)
                .orElse(null);

        if (cartDetail != null) {
            Long newQty = cartDetail.getQuantity() + quantity;
            newQty = Math.min(newQty, product.getStockQuantity());
            cartDetail.setQuantity(newQty);
        } else {
            cartDetail = new CartDetail();
            cartDetail.setUser(currentUser);
            cartDetail.setProduct(product);
            cartDetail.setQuantity(Math.min(quantity, product.getStockQuantity()));
        }
        cartDetailRepository.save(cartDetail);

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

        // Không cho update số lượng nếu sản phẩm không còn khả dụng
        if (!isAvailable(cartDetail.getProduct())) {
            response.put("success", false);
            response.put("message", "Sản phẩm không còn khả dụng!");
            return ResponseEntity.ok(response);
        }

        Long stock = cartDetail.getProduct().getStockQuantity();
        if (quantity < 1) quantity = 1;
        if (quantity > stock.intValue()) quantity = stock.intValue();

        cartDetail.setQuantity(Long.valueOf(quantity));
        cartDetailRepository.save(cartDetail);

        java.math.BigDecimal lineTotal = cartDetail.getProduct().getPrice()
                .multiply(java.math.BigDecimal.valueOf(quantity));

        // Tính lại tổng (chỉ tính sản phẩm có thể mua)
        List<CartDetail> allItems = cartDetailRepository.findByUser(currentUser);
        java.math.BigDecimal subtotal = allItems.stream()
                .filter(ci -> isAvailable(ci.getProduct()))
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal shippingFee = subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal("30000");

        int totalQty = cartDetailRepository.countTotalQuantityByUser(currentUser);

        response.put("success",     true);
        response.put("lineTotal",   lineTotal);
        response.put("subtotal",    subtotal);
        response.put("shippingFee", shippingFee);
        response.put("total",       subtotal.add(shippingFee));
        response.put("cartCount",   totalQty);
        response.put("quantity",    quantity);
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

        // Tính lại tổng (chỉ tính sản phẩm có thể mua)
        java.math.BigDecimal subtotal = allItems.stream()
                .filter(ci -> isAvailable(ci.getProduct()))
                .map(ci -> ci.getProduct().getPrice()
                        .multiply(java.math.BigDecimal.valueOf(ci.getQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal shippingFee = subtotal.compareTo(new java.math.BigDecimal("150000")) >= 0
                ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal("30000");

        int totalQty = cartDetailRepository.countTotalQuantityByUser(currentUser);

        response.put("success",     true);
        response.put("subtotal",    subtotal);
        response.put("shippingFee", shippingFee);
        response.put("total",       subtotal.add(shippingFee));
        response.put("cartCount",   totalQty);
        response.put("isEmpty",     allItems.isEmpty());
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // GET (AJAX): Đếm số lượng trong giỏ
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