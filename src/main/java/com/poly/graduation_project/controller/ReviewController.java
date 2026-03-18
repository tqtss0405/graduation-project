package com.poly.graduation_project.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.model.OrderDetail;
import com.poly.graduation_project.model.Review;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.OrderDetailRepository;
import com.poly.graduation_project.repository.OrderRepository;
import com.poly.graduation_project.repository.ReviewRepository;
import com.poly.graduation_project.service.SessionService;

import jakarta.servlet.http.HttpSession;

@Controller
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private SessionService sessionService;

    // ========================================================
    // USER: Xem form viết review cho 1 order detail
    // ========================================================
    @GetMapping("/user/review/write/{orderDetailId}")
    public String showWriteReviewForm(@PathVariable Integer orderDetailId, Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");

        OrderDetail orderDetail = orderDetailRepository.findById(orderDetailId).orElse(null);
        if (orderDetail == null)
            return "redirect:/user/order-details";

        Order order = orderDetail.getOrder();
        if (order == null || !order.getUser().getId().equals(currentUser.getId()))
            return "redirect:/user/order-details";
        if (order.getStatus() == null || order.getStatus() != 4)
            return "redirect:/user/order-details";
        if (reviewRepository.existsByOrderDetail(orderDetail))
            return "redirect:/user/order-details?alreadyReviewed=true";

        model.addAttribute("orderDetail", orderDetail);
        return "write-review";
    }

    // ========================================================
    // USER: Submit 1 review đơn lẻ (giữ nguyên cho tương thích)
    // ========================================================
    @PostMapping("/user/review/submit")
    public String submitReview(
            @RequestParam("orderDetailId") Integer orderDetailId,
            @RequestParam("rating") Integer rating,
            @RequestParam("content") String content,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");

        OrderDetail orderDetail = orderDetailRepository.findById(orderDetailId).orElse(null);
        if (orderDetail == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng!");
            return "redirect:/user/order-details";
        }

        Order order = orderDetail.getOrder();
        if (order == null || !order.getUser().getId().equals(currentUser.getId()))
            return "redirect:/user/order-details";
        if (order.getStatus() == null || order.getStatus() != 4) {
            ra.addFlashAttribute("errorMessage", "Chỉ có thể đánh giá đơn hàng đã hoàn thành!");
            return "redirect:/user/order-details";
        }
        if (reviewRepository.existsByOrderDetail(orderDetail)) {
            ra.addFlashAttribute("errorMessage", "Bạn đã đánh giá sản phẩm này rồi!");
            return "redirect:/user/order-details";
        }
        if (rating == null || rating < 1 || rating > 5) {
            ra.addFlashAttribute("errorMessage", "Vui lòng chọn số sao!");
            return "redirect:/user/review/write/" + orderDetailId;
        }
        if (content == null || content.trim().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Vui lòng nhập nội dung đánh giá!");
            return "redirect:/user/review/write/" + orderDetailId;
        }

        Review review = new Review();
        review.setRating(rating);
        review.setContent(content.trim());
        review.setCreateAt(LocalDateTime.now());
        review.setOrderDetail(orderDetail);
        review.setUser(currentUser);
        reviewRepository.save(review);

        ra.addFlashAttribute("successMessage", "Cảm ơn bạn đã đánh giá sản phẩm! ⭐");
        return "redirect:/user/order-details";
    }

    // ========================================================
    // USER: Submit đánh giá hàng loạt (1 lần cho toàn bộ đơn)
    // POST /user/review/batch-submit
    // Params: orderDetailIds[], ratings[], contents[]
    // ========================================================
    @PostMapping("/user/review/batch-submit")
    public String batchSubmitReview(
            @RequestParam("orderDetailIds") List<Integer> orderDetailIds,
            @RequestParam("ratings") List<Integer> ratingList,
            @RequestParam("contents") List<String> contentList,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null)
            return "redirect:/login/form";

        if (orderDetailIds == null || orderDetailIds.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Không có sản phẩm nào để đánh giá!");
            return "redirect:/user/order-details";
        }

        int saved = 0;
        int skipped = 0;

        for (int i = 0; i < orderDetailIds.size(); i++) {
            Integer odId = orderDetailIds.get(i);
            Integer rating = (i < ratingList.size()) ? ratingList.get(i) : null;
            String content = (i < contentList.size()) ? contentList.get(i) : null;

            // Validate dữ liệu
            if (rating == null || rating < 1 || rating > 5) {
                skipped++;
                continue;
            }
            if (content == null || content.trim().isEmpty()) {
                skipped++;
                continue;
            }

            OrderDetail orderDetail = orderDetailRepository.findById(odId).orElse(null);
            if (orderDetail == null) {
                skipped++;
                continue;
            }

            // Kiểm tra đơn hàng thuộc về user này
            Order order = orderDetail.getOrder();
            if (order == null || !order.getUser().getId().equals(currentUser.getId())) {
                skipped++;
                continue;
            }

            // Chỉ cho đánh giá đơn hoàn thành (status=4)
            if (order.getStatus() == null || order.getStatus() != 4) {
                skipped++;
                continue;
            }

            // Bỏ qua nếu đã review rồi (không ghi đè)
            if (reviewRepository.existsByOrderDetail(orderDetail)) {
                skipped++;
                continue;
            }

            Review review = new Review();
            review.setRating(rating);
            review.setContent(content.trim());
            review.setCreateAt(LocalDateTime.now());
            review.setOrderDetail(orderDetail);
            review.setUser(currentUser);
            reviewRepository.save(review);
            saved++;
        }

        if (saved > 0) {
            String msg = saved == 1
                    ? "Cảm ơn bạn đã đánh giá sản phẩm! ⭐"
                    : "Cảm ơn bạn đã đánh giá " + saved + " sản phẩm! ⭐";
            ra.addFlashAttribute("successMessage", msg);
        } else {
            ra.addFlashAttribute("errorMessage", "Không có đánh giá nào được lưu. Có thể bạn đã đánh giá rồi!");
        }

        return "redirect:/user/order-details";
    }

    // ========================================================
    // ADMIN: Danh sách tất cả review
    // ========================================================
    @GetMapping("/admin/reviews")
    public String adminReviews(
            @RequestParam(value = "rating", required = false) Integer rating,
            Model model) {

        List<Review> reviews;
        if (rating != null && rating >= 1 && rating <= 5) {
            reviews = reviewRepository.findByRatingOrderByCreateAtDesc(rating);
        } else {
            reviews = reviewRepository.findAllByOrderByCreateAtDesc();
        }

        long totalReviews = reviewRepository.countAll();
        Double avgRating = reviewRepository.avgRatingAll();
        long thisMonth = reviewRepository.countThisMonth();

        model.addAttribute("reviews", reviews);
        model.addAttribute("totalReviews", totalReviews);
        model.addAttribute("avgRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        model.addAttribute("thisMonth", thisMonth);
        model.addAttribute("selectedRating", rating);
        return "admin-reviews";
    }

    // ========================================================
    // ADMIN: Phản hồi review
    // ========================================================
    @PostMapping("/admin/reviews/reply/{id}")
    public String replyReview(
            @PathVariable Integer id,
            @RequestParam("adminReply") String adminReply,
            RedirectAttributes ra) {

        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đánh giá!");
            return "redirect:/admin/reviews";
        }
        if (adminReply == null || adminReply.trim().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Vui lòng nhập nội dung phản hồi!");
            return "redirect:/admin/reviews";
        }

        review.setAdminReply(adminReply.trim());
        review.setAdminReplyAt(LocalDateTime.now());
        reviewRepository.save(review);

        ra.addFlashAttribute("successMessage", "Đã gửi phản hồi thành công!");
        return "redirect:/admin/reviews";
    }

    // ========================================================
    // ADMIN: Xóa review
    // ========================================================
    @PostMapping("/admin/reviews/delete/{id}")
    public String deleteReview(@PathVariable Integer id, RedirectAttributes ra) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy đánh giá!");
            return "redirect:/admin/reviews";
        }
        reviewRepository.delete(review);
        ra.addFlashAttribute("successMessage", "Đã xóa đánh giá thành công!");
        return "redirect:/admin/reviews";
    }
}