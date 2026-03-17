package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.transaction.annotation.Transactional;

import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.Review;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.model.Voucher;
import com.poly.graduation_project.repository.AddressRepository;
import com.poly.graduation_project.repository.CartDetailRepository;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.OrderDetailRepository;
import com.poly.graduation_project.repository.OrderRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.repository.ReviewRepository;
import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.repository.VoucherRepository;
import com.poly.graduation_project.service.SessionService;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Controller
public class IndexController {
    @Autowired private VoucherRepository voucherRepository;
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired SessionService sessionService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderDetailRepository orderDetailRepository;
    @Autowired CartDetailRepository cartDetailRepository;

    // ================================================
    // Trang chủ
    // ================================================
    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<Product> products = productRepository.findTop8ByActiveTrueOrderByIdDesc();
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryRepository.findAll());
        return "index";
    }

    // ================================================
    // Trang tất cả sản phẩm
    // ================================================
    @GetMapping("/products")
    public String products(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        List<Product> products = productRepository.findByActiveTrue();
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("totalQuantity", totalQuantity);
        return "products";
    }

    // ================================================
    // Trang chi tiết sản phẩm theo slug
    // ================================================
    @GetMapping("/product/{slug}")
    public String productDetail(@PathVariable("slug") String slug, Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        Product product = productRepository.findBySlug(slug);
        if (product == null) return "redirect:/products";

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);

        List<Product> related = productRepository
                .findTop4ByCategoryAndIdNotAndActiveTrue(product.getCategory(), product.getId());

        List<Review> reviews = reviewRepository.findByProductId(product.getId());
        long reviewCount = reviewRepository.countByProductId(product.getId());
        Double avgRating = reviewRepository.avgRatingByProductId(product.getId());

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", related);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("avgRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);

        Integer reviewableOrderDetailId = null;
        if (currentUser != null) {
            List<Order> completedOrders = orderRepository.findCompletedOrdersByUser(currentUser);
            outer: for (Order order : completedOrders) {
                if (order.getOrderDetails() != null) {
                    for (OrderDetail od : order.getOrderDetails()) {
                        if (od.getProduct() != null
                                && od.getProduct().getId().equals(product.getId())
                                && !reviewRepository.existsByOrderDetail(od)) {
                            reviewableOrderDetailId = od.getId();
                            break outer;
                        }
                    }
                }
            }
        }
        model.addAttribute("reviewableOrderDetailId", reviewableOrderDetailId);
        return "product-details";
    }

    @GetMapping("/about")
    public String about(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);
        return "contact";
    }

    @GetMapping("/user/favourites")
    public String favourites(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);
        return "favourites";
    }

    @GetMapping("/user/profile")
    public String profile(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        User user = userRepository.findById(currentUser.getId()).orElse(null);
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("user", user);
        return "profile";
    }

    // ================================================
    // Trang lịch sử mua hàng
    // ================================================
    @GetMapping("/user/order-details")
    public String orderDetails(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);

        List<Order> orders = orderRepository.findByUserOrderByCreateAtDesc(currentUser);

        Map<Integer, Boolean> reviewedMap = new HashMap<>();
        for (Order order : orders) {
            if (order.getOrderDetails() != null) {
                for (OrderDetail od : order.getOrderDetails()) {
                    reviewedMap.put(od.getId(), reviewRepository.existsByOrderDetail(od));
                }
            }
        }

        model.addAttribute("orders", orders);
        model.addAttribute("reviewedMap", reviewedMap);
        return "order-details";
    }

    // ================================================
    // Trang mã giảm giá
    // ✅ Merge từ VoucherController vào đây, tránh duplicate mapping
    // ✅ @Transactional để load lazy collection v.orders
    // ================================================
    @Transactional
    @GetMapping("/vouchers")
    public String vouchersPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        LocalDateTime now = LocalDateTime.now();

        List<Voucher> vouchers = voucherRepository.findAll()
            .stream()
            .sorted(Comparator.comparing((Voucher v) -> {
                boolean isValid = Boolean.TRUE.equals(v.getActive())
                    && (v.getEndAt() == null || v.getEndAt().isAfter(now))
                    && (v.getQuantity() == null || v.getQuantity() > 0);
                return isValid ? 0 : 1;
            }))
            .toList();

        // ✅ Tính sẵn isExpired / isLow trong controller, tránh dùng T() trong Thymeleaf
        Map<Integer, Boolean> isExpiredMap = new HashMap<>();
        Map<Integer, Boolean> isLowMap     = new HashMap<>();
        for (Voucher v : vouchers) {
            boolean expired = !Boolean.TRUE.equals(v.getActive())
                || (v.getEndAt() != null && v.getEndAt().isBefore(now))
                || (v.getQuantity() != null && v.getQuantity() <= 0);
            boolean low = !expired
                && v.getQuantity() != null
                && v.getQuantity() > 0
                && v.getQuantity() <= 5;
            isExpiredMap.put(v.getId(), expired);
            isLowMap.put(v.getId(), low);
        }

        int totalQuantity = 0;
        if (currentUser != null) {
            List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
            totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        }

        model.addAttribute("vouchers", vouchers);
        model.addAttribute("isExpiredMap", isExpiredMap);
        model.addAttribute("isLowMap", isLowMap);
        model.addAttribute("totalQuantity", totalQuantity);
        return "vouchers";
    }
}