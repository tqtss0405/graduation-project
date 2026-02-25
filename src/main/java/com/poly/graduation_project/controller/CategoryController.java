package com.poly.graduation_project.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.model.Category;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.ProductRepository;

@Controller
public class CategoryController {
    @Autowired
    CategoryRepository categoryRepo;

    @Autowired
    ProductRepository productRepo;

    @GetMapping("/admin/categories")
    public String listCategories(Model model) {
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

        // Kiểm tra trùng lặp tên
        if (category.getId() == null) {
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

        if (category.getActive() == null) {
            category.setActive(false);
        }

        categoryRepo.save(category);
        ra.addFlashAttribute("successMessage", "Lưu thông tin danh mục thành công!");
        return "redirect:/admin/categories";
    }
    @GetMapping("/admin/categories/delete/{id}")
    public String deleteCategory(@PathVariable("id") int id, RedirectAttributes ra) {
        try {
            Category category = categoryRepo.findById(id).orElse(null);
            if (category == null) {
                ra.addFlashAttribute("errorMessage", "Không tìm thấy danh mục!");
                return "redirect:/admin/categories";
            }

            // KIỂM TRA: Danh mục có sản phẩm nào không?
            List<Product> products = productRepo.findByCategory(category);
            
            if (products != null && !products.isEmpty()) {
                // TRƯỜNG HỢP 1: Có sản phẩm -> Chỉ thực hiện ẨN (Soft Delete)
                category.setActive(false);
                categoryRepo.save(category);
                ra.addFlashAttribute("successMessage", "Danh mục '" + category.getName() + "' đang có " + products.size() + " sản phẩm nên hệ thống đã chuyển sang trạng thái ẨN để bảo vệ dữ liệu.");
            } else {
                // TRƯỜNG HỢP 2: Không có sản phẩm -> XÓA vĩnh viễn (Hard Delete)
                categoryRepo.delete(category);
                ra.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn danh mục '" + category.getName() + "' thành công.");
            }
            
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi xử lý: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
