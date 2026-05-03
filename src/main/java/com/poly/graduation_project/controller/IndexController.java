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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Autowired
    FavouriteRepository favouriteRepository;

    private static final int PRODUCTS_PER_PAGE = 9;
    private static final int CATS_PER_PAGE = 5;

    // ================================================
    // Trang chủ — ưu tiên sản phẩm còn hàng lên trên
    // ================================================
    @GetMapping("/home")
    public String home(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");

        List<Product> allActive = productRepository.findByActiveTrue().stream()
                .filter(p -> p.getCategory() == null || Boolean.TRUE.equals(p.getCategory().getActive()))
                .collect(Collectors.toList());

        Map<Integer, Long> soldMap = new HashMap<>();
        for (Product p : allActive) {
            long sold = orderDetailRepository.sumQuantityByProductId(p.getId());
            soldMap.put(p.getId(), sold);
        }

        List<Product> products = allActive.stream()
                .sorted((a, b) -> {
                    boolean aStock = a.getStockQuantity() != null && a.getStockQuantity() > 0;
                    boolean bStock = b.getStockQuantity() != null && b.getStockQuantity() > 0;
                    if (aStock != bStock)
                        return Boolean.compare(bStock, aStock);

                    long soldA = soldMap.getOrDefault(a.getId(), 0L);
                    long soldB = soldMap.getOrDefault(b.getId(), 0L);
                    Double ratingA = reviewRepository.avgRatingByProductId(a.getId());
                    Double ratingB = reviewRepository.avgRatingByProductId(b.getId());
                    double ra = ratingA != null ? ratingA : 0.0;
                    double rb = ratingB != null ? ratingB : 0.0;

                    double scoreA = soldA * 0.6 + ra * 20 * 0.4;
                    double scoreB = soldB * 0.6 + rb * 20 * 0.4;
                    return Double.compare(scoreB, scoreA);
                })
                .limit(8)
                .collect(Collectors.toList());

        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .collect(Collectors.toList());

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        Long totalQuantity = cartItems.stream().mapToLong(CartDetail::getQuantity).sum();

        java.util.Set<Integer> favouriteProductIds = new java.util.HashSet<>();
        if (currentUser != null) {
            favouriteRepository.findByUser(currentUser)
                    .forEach(fav -> favouriteProductIds.add(fav.getProduct().getId()));
        }

        Map<Integer, Double> ratingMap = new HashMap<>();
        Map<Integer, Long> soldCountMap = new HashMap<>();
        for (Product p : products) {
            Double avg = reviewRepository.avgRatingByProductId(p.getId());
            ratingMap.put(p.getId(), avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
            soldCountMap.put(p.getId(), soldMap.getOrDefault(p.getId(), 0L));
        }

        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("products", products);
        model.addAttribute("categories", activeCategories);
        model.addAttribute("favouriteProductIds", favouriteProductIds);
        model.addAttribute("ratingMap", ratingMap);
        model.addAttribute("soldCountMap", soldCountMap);
        return "index";
    }

    // ================================================
    // Trang sản phẩm — ưu tiên còn hàng, sort/filter/phân trang
    // ================================================
    @GetMapping("/products")
    public String products(
            @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "priceRange", required = false, defaultValue = "") String priceRange,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "sort", required = false, defaultValue = "newest") String sort,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "catPage", required = false, defaultValue = "1") int catPage,
            Model model, HttpSession session) {

        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        Long totalQuantity = cartItems.stream().mapToLong(CartDetail::getQuantity).sum();

        if (!priceRange.isEmpty()) {
            switch (priceRange) {
                case "0-100000":
                    minPrice = BigDecimal.ZERO;
                    maxPrice = new BigDecimal("100000");
                    break;
                case "100000-200000":
                    minPrice = new BigDecimal("100000");
                    maxPrice = new BigDecimal("200000");
                    break;
                case "200000-500000":
                    minPrice = new BigDecimal("200000");
                    maxPrice = new BigDecimal("500000");
                    break;
                case "500000+":
                    minPrice = new BigDecimal("500000");
                    maxPrice = null;
                    break;
            }
        }

        List<Product> all = productRepository.findByActiveTrue().stream()
                .filter(p -> p.getCategory() == null || Boolean.TRUE.equals(p.getCategory().getActive()))
                .collect(Collectors.toList());

        final String kw = removeAccent(keyword.trim());
        String[] words = kw.split("\\s+");
        all = all.stream().filter(p -> {
            String name = removeAccent(p.getName());
            return java.util.Arrays.stream(words).allMatch(name::contains);
        }).collect(Collectors.toList());

        final Integer finalCatId = categoryId;
        if (categoryId != null) {
            all = all.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(finalCatId))
                    .collect(Collectors.toList());
        }

        final BigDecimal fMin = minPrice, fMax = maxPrice;
        if (minPrice != null || maxPrice != null) {
            all = all.stream().filter(p -> {
                if (p.getPrice() == null)
                    return false;
                if (fMin != null && p.getPrice().compareTo(fMin) < 0)
                    return false;
                if (fMax != null && p.getPrice().compareTo(fMax) > 0)
                    return false;
                return true;
            }).collect(Collectors.toList());
        }

        switch (sort) {
            case "price-asc":
                all.sort(Comparator.comparing(Product::getPrice));
                break;
            case "price-desc":
                all.sort(Comparator.comparing(Product::getPrice).reversed());
                break;
            default:
                all.sort(Comparator.comparing(Product::getId).reversed());
                break;
        }

        all.sort(Comparator.comparingInt(p -> (p.getStockQuantity() != null && p.getStockQuantity() > 0) ? 0 : 1));

        int totalProducts = all.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalProducts / PRODUCTS_PER_PAGE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int fromIdx = (page - 1) * PRODUCTS_PER_PAGE;
        int toIdx = Math.min(fromIdx + PRODUCTS_PER_PAGE, totalProducts);
        List<Product> pagedProducts = totalProducts > 0 ? all.subList(fromIdx, toIdx) : all;

        List<Category> allCats = categoryRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .collect(Collectors.toList());

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

        model.addAttribute("products", pagedProducts);
        model.addAttribute("categories", pagedCats);
        model.addAttribute("totalQuantity", totalQuantity);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentCategoryId", categoryId);
        model.addAttribute("currentCategoryName", currentCategoryName);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentMinPrice", minPrice);
        model.addAttribute("currentMaxPrice", maxPrice);
        model.addAttribute("currentPriceRange", priceRange);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("catPage", catPage);
        model.addAttribute("totalCatPages", totalCatPages);
        return "products";
    }

    // ================================================
    // Trang chi tiết sản phẩm
    // ================================================
    @GetMapping("/product/{slug}")
    public String productDetail(@PathVariable("slug") String slug, Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        Product product = productRepository.findBySlug(slug);

        if (product == null
                || !Boolean.TRUE.equals(product.getActive())
                || (product.getCategory() != null && !Boolean.TRUE.equals(product.getCategory().getActive()))) {
            return "redirect:/products";
        }

        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        Long totalQuantity = cartItems.stream().mapToLong(CartDetail::getQuantity).sum();
        model.addAttribute("totalQuantity", totalQuantity);

        List<Product> related = productRepository
                .findTop4ByCategoryAndIdNotAndActiveTrue(product.getCategory(), product.getId())
                .stream()
                .filter(p -> p.getCategory() == null || Boolean.TRUE.equals(p.getCategory().getActive()))
                .collect(Collectors.toList());

        List<Review> reviews = reviewRepository.findByProductId(product.getId());
        long reviewCount = reviewRepository.countByProductId(product.getId());
        Double avgRating = reviewRepository.avgRatingByProductId(product.getId());

        boolean isFavourite = false;
        if (currentUser != null) {
            isFavourite = favouriteRepository.existsByUserAndProduct(currentUser, product);
        }

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", related);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("avgRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        model.addAttribute("isFavourite", isFavourite);

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
        model.addAttribute("totalQuantity", cartItems.stream().mapToLong(CartDetail::getQuantity).sum());
        return "about";
    }

    @GetMapping("/contact")
    public String contact(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        model.addAttribute("totalQuantity", cartItems.stream().mapToLong(CartDetail::getQuantity).sum());
        return "contact";
    }

    @GetMapping("/user/order-details")
    public String orderDetails(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        model.addAttribute("totalQuantity", cartItems.stream().mapToLong(CartDetail::getQuantity).sum());

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
        model.addAttribute("orders", orders);
        model.addAttribute("reviewedMap", reviewedMap);
        model.addAttribute("hasUnreviewedMap", hasUnreviewedMap);
        return "order-details";
    }

    // ================================================
    // Trang vouchers — sort ưu tiên, phân trang 10/trang
    // ================================================
    @GetMapping("/vouchers")
    public String vouchersPage(
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "tab", required = false, defaultValue = "active") String tab,
            Model model, HttpSession session) {

        User currentUser = (User) session.getAttribute("currentUser");
        List<Voucher> allVouchers = voucherRepository.findAll();

        Long totalQuantity = 0L;
        if (currentUser != null) {
            List<CartDetail> c = cartDetailRepository.findByUser(currentUser);
            totalQuantity = c.stream().mapToLong(CartDetail::getQuantity).sum();
        }

        // ── Tập hợp ID voucher user đã dùng (bỏ qua đơn hủy) ──────────────
        Set<Integer> usedVoucherIds = new HashSet<>();
        if (currentUser != null) {
            for (Voucher v : allVouchers) {
                if (orderRepository.existsByUserAndVoucherId(currentUser, v.getId())) {
                    usedVoucherIds.add(v.getId());
                }
            }
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        final Set<Integer> finalUsedIds = usedVoucherIds;

        List<Voucher> activeVouchersList = new java.util.ArrayList<>();
        List<Voucher> historyVouchersList = new java.util.ArrayList<>();

        for (Voucher v : allVouchers) {
            int rank = voucherRank(v, finalUsedIds, now);
            if (rank == 0) {
                activeVouchersList.add(v);
            } else {
                historyVouchersList.add(v);
            }
        }

        int activeCount = activeVouchersList.size();
        int historyCount = historyVouchersList.size();

        List<Voucher> targetList = "history".equals(tab) ? historyVouchersList : activeVouchersList;

        // ── Sort: ưu tiên voucher còn dùng được lên đầu ────────────────────
        targetList.sort((a, b) -> {
            int rankA = voucherRank(a, finalUsedIds, now);
            int rankB = voucherRank(b, finalUsedIds, now);
            if (rankA != rankB) return Integer.compare(rankA, rankB);
            // Cùng nhóm → voucher mới hơn (id lớn hơn) lên trên
            return Integer.compare(b.getId(), a.getId());
        });

        // ── Phân trang 10 voucher/trang ────────────────────────────────────
        final int PAGE_SIZE = 10;
        int totalVouchers = targetList.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalVouchers / PAGE_SIZE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int fromIdx = (page - 1) * PAGE_SIZE;
        int toIdx   = Math.min(fromIdx + PAGE_SIZE, totalVouchers);
        List<Voucher> pagedVouchers = targetList.subList(fromIdx, toIdx);

        model.addAttribute("vouchers",      pagedVouchers);
        model.addAttribute("usedVoucherIds", usedVoucherIds);
        model.addAttribute("totalQuantity",  totalQuantity);
        model.addAttribute("currentPage",    page);
        model.addAttribute("totalPages",     totalPages);
        model.addAttribute("totalVouchers",  totalVouchers);
        model.addAttribute("activeCount",    activeCount);
        model.addAttribute("historyCount",   historyCount);
        model.addAttribute("currentTab",     tab);
        return "vouchers";
    }

    /**
     * Rank dùng để sort voucher:
     *   0 = còn dùng được (active, chưa hết hạn, còn lượt, chưa dùng)
     *   1 = đã dùng rồi
     *   2 = hết hạn / bị tắt / hết lượt
     */
    private int voucherRank(Voucher v, Set<Integer> usedIds, java.time.LocalDateTime now) {
        boolean inactive = !Boolean.TRUE.equals(v.getActive());
        boolean expired  = v.getEndAt() != null && v.getEndAt().isBefore(now);
        boolean outOfQty = v.getQuantity() != null && v.getQuantity() <= 0;
        boolean used     = usedIds.contains(v.getId());

        if (inactive || expired || outOfQty) return 2;
        if (used) return 1;
        return 0;
    }

    public static String removeAccent(String s) {
        if (s == null) return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }
}