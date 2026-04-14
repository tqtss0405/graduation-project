package com.poly.graduation_project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Category;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.service.SessionService;

@Controller
public class CategoryController {
    @Autowired
    CategoryRepository categoryRepo;

    @Autowired
    ProductRepository productRepo;

    @Autowired
    private SessionService sessionService;

    @GetMapping("/admin/categories")
    public String listCategories(Model model) {
        User currentUser = (User) sessionService.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("categories", categoryRepo.findAll());
        return "admin-categories";
    }

    @PostMapping("/admin/categories/save")
    public String saveCategory(@ModelAttribute Category category, RedirectAttributes ra) {

        // Kiểm tra trống Tên
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Tên danh mục không được để trống!");
            return "redirect:/admin/categories";
        }

        boolean isNew = (category.getId() == null);

        // Kiểm tra trùng lặp tên
        if (isNew) {
            if (categoryRepo.existsByName(category.getName().trim())) {
                ra.addFlashAttribute("errorMessage", "Tên danh mục đã tồn tại!");
                return "redirect:/admin/categories";
            }
        } else {
            if (categoryRepo.existsByNameAndIdNot(category.getName().trim(), category.getId())) {
                ra.addFlashAttribute("errorMessage", "Tên danh mục đã bị trùng với danh mục khác!");
                return "redirect:/admin/categories";
            }
        }

        // Chuẩn hóa dữ liệu
        category.setName(category.getName().trim());

        if (category.getColor() == null || category.getColor().trim().isEmpty()) {
            category.setColor("linear-gradient(135deg, #667eea, #764ba2)");
        } else {
            category.setColor(category.getColor().trim());
        }

        if (category.getIcon() == null || category.getIcon().trim().isEmpty()) {
            category.setIcon("fa-solid fa-graduation-cap");
        } else {
            category.setIcon(category.getIcon().trim());
        }

        // Thêm mới: luôn active = true, không phụ thuộc vào form
        if (isNew) {
            category.setActive(true);
        } else {
            // Cập nhật: active do form gửi lên (checkbox), nếu null thì false
            if (category.getActive() == null) {
                category.setActive(false);
            }
        }

        categoryRepo.save(category);
        ra.addFlashAttribute("successMessage",
                isNew ? "Thêm danh mục '" + category.getName() + "' thành công!"
                        : "Cập nhật danh mục '" + category.getName() + "' thành công!");
        return "redirect:/admin/categories";
    }

    // ── Ẩn danh mục (soft delete) ──────────────────────────────────────────────
    @GetMapping("/admin/categories/hide/{id}")
    public String hideCategory(@PathVariable("id") int id, RedirectAttributes ra) {
        Category category = categoryRepo.findById(id).orElse(null);
        if (category == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy danh mục!");
            return "redirect:/admin/categories";
        }
        category.setActive(false);
        categoryRepo.save(category);
        ra.addFlashAttribute("successMessage",
                "Đã ẩn danh mục '" + category.getName() + "' thành công.");
        return "redirect:/admin/categories";
    }

    // ── Hiện danh mục (restore) ────────────────────────────────────────────────
    @GetMapping("/admin/categories/show/{id}")
    public String showCategory(@PathVariable("id") int id, RedirectAttributes ra) {
        Category category = categoryRepo.findById(id).orElse(null);
        if (category == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy danh mục!");
            return "redirect:/admin/categories";
        }
        category.setActive(true);
        categoryRepo.save(category);
        ra.addFlashAttribute("successMessage",
                "Đã hiển thị danh mục '" + category.getName() + "' thành công.");
        return "redirect:/admin/categories";
    }

    // ── Xóa vĩnh viễn (chỉ khi không có sản phẩm) ────────────────────────────
    @GetMapping("/admin/categories/delete/{id}")
    public String deleteCategory(@PathVariable("id") int id, RedirectAttributes ra) {
        try {
            Category category = categoryRepo.findById(id).orElse(null);
            if (category == null) {
                ra.addFlashAttribute("errorMessage", "Không tìm thấy danh mục!");
                return "redirect:/admin/categories";
            }

            List<Product> products = productRepo.findByCategory(category);

            if (products != null && !products.isEmpty()) {
                // Có sản phẩm → không cho xóa vĩnh viễn, chỉ ẩn
                ra.addFlashAttribute("errorMessage",
                        "Không thể xóa vĩnh viễn danh mục '" + category.getName() +
                                "' vì đang có " + products.size() + " sản phẩm! Hãy dùng chức năng Ẩn.");
            } else {
                // Không có sản phẩm → xóa vĩnh viễn
                categoryRepo.delete(category);
                ra.addFlashAttribute("successMessage",
                        "Đã xóa vĩnh viễn danh mục '" + category.getName() + "' thành công.");
            }

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi xử lý: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}