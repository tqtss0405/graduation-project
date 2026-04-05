package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Category;
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
import com.poly.graduation_project.repository.FavouriteRepository;
import com.poly.graduation_project.service.SessionService;

import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Autowired FavouriteRepository favouriteRepository;

    private static final int PRODUCTS_PER_PAGE = 9;
    private static final int CATS_PER_PAGE = 5;

    // ================================================
    // Trang chủ — ưu tiên sản phẩm còn hàng lên trên
    // ================================================
    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");

        // Lấy 8 sản phẩm mới nhất đang active, sau đó sort: còn hàng lên trước
        List<Product> products = productRepository.findTop8ByActiveTrueOrderByIdDesc()
                .stream()
                .sorted(Comparator.comparingInt((Product p) ->
                        (p.getStockQuantity() != null && p.getStockQuantity() > 0) ? 0 : 1))
                .collect(Collectors.toList());

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();

        java.util.Set<Integer> favouriteProductIds = new java.util.HashSet<>();
        if (currentUser != null) {
            favouriteRepository.findByUser(currentUser)
                    .forEach(fav -> favouriteProductIds.add(fav.getProduct().getId()));
        }

        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("products", products);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("favouriteProductIds", favouriteProductIds);
        return "index";
    }

    // ================================================
    // Trang sản phẩm — ưu tiên còn hàng, sort/filter/phân trang
    // ================================================
    @GetMapping("/products")
    public String products(
            @RequestParam(name = "keyword",    required = false, defaultValue = "") String keyword,
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "priceRange", required = false, defaultValue = "") String priceRange,
            @RequestParam(name = "minPrice",   required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice",   required = false) BigDecimal maxPrice,
            @RequestParam(name = "sort",       required = false, defaultValue = "newest") String sort,
            @RequestParam(name = "page",       required = false, defaultValue = "1") int page,
            @RequestParam(name = "catPage",    required = false, defaultValue = "1") int catPage,
            Model model, HttpSession session) {

        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();

        // --- Parse preset khoảng giá ---
        if (!priceRange.isEmpty()) {
            switch (priceRange) {
                case "0-100000":    minPrice = BigDecimal.ZERO;             maxPrice = new BigDecimal("100000");  break;
                case "100000-200000": minPrice = new BigDecimal("100000"); maxPrice = new BigDecimal("200000");  break;
                case "200000-500000": minPrice = new BigDecimal("200000"); maxPrice = new BigDecimal("500000");  break;
                case "500000+":     minPrice = new BigDecimal("500000");    maxPrice = null;                      break;
            }
        }

        List<Product> all = productRepository.findByActiveTrue();

        // --- Lọc keyword ---
        final String kw = removeAccent(keyword.trim());
        String[] words = kw.split("\\s+");
        all = all.stream().filter(p -> {
            String name = removeAccent(p.getName());
            return java.util.Arrays.stream(words).allMatch(name::contains);
        }).collect(Collectors.toList());

        // --- Lọc danh mục ---
        final Integer finalCatId = categoryId;
        if (categoryId != null) {
            all = all.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(finalCatId))
                    .collect(Collectors.toList());
        }

        // --- Lọc giá ---
        final BigDecimal fMin = minPrice, fMax = maxPrice;
        if (minPrice != null || maxPrice != null) {
            all = all.stream().filter(p -> {
                if (p.getPrice() == null) return false;
                if (fMin != null && p.getPrice().compareTo(fMin) < 0) return false;
                if (fMax != null && p.getPrice().compareTo(fMax) > 0) return false;
                return true;
            }).collect(Collectors.toList());
        }

        // --- Sắp xếp theo tiêu chí người dùng chọn ---
        switch (sort) {
            case "price-asc":  all.sort(Comparator.comparing(Product::getPrice)); break;
            case "price-desc": all.sort(Comparator.comparing(Product::getPrice).reversed()); break;
            default:           all.sort(Comparator.comparing(Product::getId).reversed()); break;
        }

        // --- Ưu tiên sản phẩm CÒN HÀNG lên trước (stable sort giữ thứ tự trong nhóm) ---
        all.sort(Comparator.comparingInt(p ->
                (p.getStockQuantity() != null && p.getStockQuantity() > 0) ? 0 : 1));

        // --- Phân trang sản phẩm ---
        int totalProducts = all.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalProducts / PRODUCTS_PER_PAGE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int fromIdx = (page - 1) * PRODUCTS_PER_PAGE;
        int toIdx = Math.min(fromIdx + PRODUCTS_PER_PAGE, totalProducts);
        List<Product> pagedProducts = totalProducts > 0 ? all.subList(fromIdx, toIdx) : all;

        // --- Phân trang danh mục sidebar ---
        List<Category> allCats = categoryRepository.findAll();
        int totalCats = allCats.size();
        int totalCatPages = Math.max(1, (int) Math.ceil((double) totalCats / CATS_PER_PAGE));
        if (catPage < 1) catPage = 1;
        if (catPage > totalCatPages) catPage = totalCatPages;
        int catFrom = (catPage - 1) * CATS_PER_PAGE;
        int catTo = Math.min(catFrom + CATS_PER_PAGE, totalCats);
        List<Category> pagedCats = allCats.subList(catFrom, catTo);

        String currentCategoryName = null;
        if (categoryId != null) {
            currentCategoryName = allCats.stream()
                    .filter(c -> c.getId().equals(categoryId))
                    .map(Category::getName).findFirst().orElse(null);
        }

        model.addAttribute("products",            pagedProducts);
        model.addAttribute("categories",          pagedCats);
        model.addAttribute("totalQuantity",       totalQuantity);
        model.addAttribute("keyword",             keyword);
        model.addAttribute("currentCategoryId",   categoryId);
        model.addAttribute("currentCategoryName", currentCategoryName);
        model.addAttribute("currentSort",         sort);
        model.addAttribute("currentMinPrice",     minPrice);
        model.addAttribute("currentMaxPrice",     maxPrice);
        model.addAttribute("currentPriceRange",   priceRange);
        model.addAttribute("currentPage",         page);
        model.addAttribute("totalPages",          totalPages);
        model.addAttribute("totalProducts",       totalProducts);
        model.addAttribute("catPage",             catPage);
        model.addAttribute("totalCatPages",       totalCatPages);
        return "products";
    }

    // ================================================
    // Trang chi tiết sản phẩm
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

        List<Review> reviews   = reviewRepository.findByProductId(product.getId());
        long reviewCount       = reviewRepository.countByProductId(product.getId());
        Double avgRating       = reviewRepository.avgRatingByProductId(product.getId());

        boolean isFavourite = false;
        if (currentUser != null) {
            isFavourite = favouriteRepository.existsByUserAndProduct(currentUser, product);
        }

        model.addAttribute("product",       product);
        model.addAttribute("relatedProducts", related);
        model.addAttribute("reviews",       reviews);
        model.addAttribute("reviewCount",   reviewCount);
        model.addAttribute("avgRating",     avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        model.addAttribute("isFavourite",   isFavourite);

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
        model.addAttribute("totalQuantity", cartItems.stream().mapToInt(CartDetail::getQuantity).sum());
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        model.addAttribute("totalQuantity", cartItems.stream().mapToInt(CartDetail::getQuantity).sum());
        return "contact";
    }

    @GetMapping("/user/order-details")
    public String orderDetails(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        model.addAttribute("totalQuantity", cartItems.stream().mapToInt(CartDetail::getQuantity).sum());

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
            if (order.getStatus() != null && order.getStatus() == 4 && order.getOrderDetails() != null) {
                boolean hasUnreviewed = order.getOrderDetails().stream()
                        .anyMatch(od -> Boolean.FALSE.equals(reviewedMap.get(od.getId())));
                hasUnreviewedMap.put(order.getId(), hasUnreviewed);
            }
        }
        model.addAttribute("orders",          orders);
        model.addAttribute("reviewedMap",     reviewedMap);
        model.addAttribute("hasUnreviewedMap", hasUnreviewedMap);
        return "order-details";
    }

    @GetMapping("/vouchers")
    public String vouchersPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<Voucher> vouchers = voucherRepository.findAll();
        int totalQuantity = 0;
        if (currentUser != null) {
            List<CartDetail> c = cartDetailRepository.findByUser(currentUser);
            totalQuantity = c.stream().mapToInt(CartDetail::getQuantity).sum();
        }
        model.addAttribute("vouchers",      vouchers);
        model.addAttribute("totalQuantity", totalQuantity);
        return "vouchers";
    }

    public static String removeAccent(String s) {
        if (s == null) return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }
}