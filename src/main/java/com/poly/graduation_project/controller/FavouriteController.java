package com.poly.graduation_project.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.CartDetail;
import com.poly.graduation_project.model.Favourite;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.CartDetailRepository;
import com.poly.graduation_project.service.FavouriteService;

import jakarta.servlet.http.HttpSession;

@Controller
public class FavouriteController {

    @Autowired
    private FavouriteService favouriteService;

    @Autowired
    private CartDetailRepository cartDetailRepository;

    // ============================================================
    // GET: Trang danh sách yêu thích
    // ============================================================
    @GetMapping("/user/favourites")
    public String favouritesPage(Model model, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null)
            return "redirect:/login/form";

        List<Favourite> favorites = favouriteService.getByUser(currentUser);
        List<CartDetail> cartItems = cartDetailRepository.findByUser(currentUser);
        int totalQuantity = cartItems.stream().mapToInt(CartDetail::getQuantity).sum();

        model.addAttribute("favorites", favorites);
        model.addAttribute("totalQuantity", totalQuantity);
        return "favourites";
    }

    // ============================================================
    // POST (AJAX): Toggle yêu thích (dùng từ trang chi tiết sản phẩm)
    // ============================================================
    @PostMapping("/user/favourites/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleFavourite(
            @RequestParam("productId") Integer productId,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            response.put("success", false);
            response.put("redirect", "/login/form");
            return ResponseEntity.ok(response);
        }

        boolean isNowFavourite = favouriteService.toggleFavourite(currentUser, productId);
        response.put("success", true);
        response.put("isFavourite", isNowFavourite);
        response.put("message", isNowFavourite ? "Đã thêm vào yêu thích!" : "Đã bỏ khỏi yêu thích!");
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // GET: Xóa khỏi yêu thích (dùng từ trang danh sách)
    // ============================================================
    @GetMapping("/user/favourites/remove/{id}")
    public String removeFavourite(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes ra) {

        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null)
            return "redirect:/login/form";

        favouriteService.removeById(id, currentUser);
        ra.addFlashAttribute("successMessage", "Đã xóa sách khỏi danh sách yêu thích!");
        return "redirect:/user/favourites";
    }
}