package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IndexController {

    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    SessionService sessionService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    ReviewRepository reviewRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderDetailRepository orderDetailRepository;
    @Autowired
    CartDetailRepository cartDetailRepository;

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
    // Trang tất cả sản phẩm - có filter, sort, phân trang
    // ================================================
    @GetMapping("/products")
    public String products(
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "sort", required = false, defaultValue = "newest") String sortType,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model, HttpSession session) {

        User currentUser = (User) session.getAttribute("currentUser");

        int totalQuantity = 0;
        if (currentUser != null) {
            List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
            totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        }

        BigDecimal finalMinPrice = (minPrice != null) ? minPrice : BigDecimal.ZERO;
        BigDecimal finalMaxPrice = (maxPrice != null) ? maxPrice : new BigDecimal("999999999");

        // Xử lý sắp xếp
        Sort sort;
        if ("price-asc".equals(sortType)) {
            sort = Sort.by("price").ascending();
        } else if ("price-desc".equals(sortType)) {
            sort = Sort.by("price").descending();
        } else {
            sort = Sort.by("id").descending();
        }

        // 9 sản phẩm mỗi trang
        Pageable pageable = PageRequest.of(page, 9, sort);

        Page<Product> productPage;

        if (categoryId != null) {
            com.poly.graduation_project.model.Category category = categoryRepository.findById(categoryId).orElse(null);
            if (category != null) {
                productPage = productRepository.findByCategoryAndActiveTrueAndPriceBetween(category, finalMinPrice,
                        finalMaxPrice, pageable);
                model.addAttribute("currentCategoryName", category.getName()); // ✅ Truyền tên danh mục
            } else {
                productPage = productRepository.findByActiveTrueAndPriceBetween(finalMinPrice, finalMaxPrice, pageable);
                model.addAttribute("currentCategoryName", null);
            }
            model.addAttribute("currentCategoryId", categoryId);
        } else {
            productPage = productRepository.findByActiveTrueAndPriceBetween(finalMinPrice, finalMaxPrice, pageable);
            model.addAttribute("currentCategoryId", null);
            model.addAttribute("currentCategoryName", null);
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalItems", productPage.getTotalElements());
        model.addAttribute("currentMinPrice", minPrice);
        model.addAttribute("currentMaxPrice", maxPrice);
        model.addAttribute("currentSort", sortType);
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
        if (product == null)
            return "redirect:/products";

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

    // ================================================
    // Các trang tĩnh
    // ================================================
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

        Map<Integer, Boolean> hasUnreviewedMap = new HashMap<>();
        for (Order order : orders) {
            if (order.getStatus() != null && order.getStatus() == 4
                    && order.getOrderDetails() != null) {
                boolean hasUnreviewed = order.getOrderDetails().stream()
                        .anyMatch(od -> Boolean.FALSE.equals(reviewedMap.get(od.getId())));
                hasUnreviewedMap.put(order.getId(), hasUnreviewed);
            }
        }

        model.addAttribute("orders", orders);
        model.addAttribute("reviewedMap", reviewedMap);
        model.addAttribute("hasUnreviewedMap", hasUnreviewedMap);
        return "order-details";
    }

    // ================================================
    // Trang mã giảm giá
    // ================================================
    @GetMapping("/vouchers")
    public String vouchersPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<Voucher> vouchers = voucherRepository.findAll();
        int totalQuantity = 0;
        if (currentUser != null) {
            List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
            totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();
        }
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("totalQuantity", totalQuantity);
        return "vouchers";
    }
}