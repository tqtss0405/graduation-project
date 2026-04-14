package com.poly.graduation_project.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.dto.ProductDTO;
import com.poly.graduation_project.helper.SlugUtils;
import com.poly.graduation_project.model.Author;
import com.poly.graduation_project.model.Image;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.repository.AuthorRepository;
import com.poly.graduation_project.repository.CartDetailRepository;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.ImageRepository;
import com.poly.graduation_project.repository.OrderDetailRepository;
import com.poly.graduation_project.repository.ProductRepository;
import com.poly.graduation_project.service.SessionService;
import com.poly.graduation_project.model.User;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.validation.Valid;

@MultipartConfig
@Controller
public class ProductController {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Autowired private ImageRepository productImageRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private CartDetailRepository cartDetailRepo;
    @Autowired private OrderDetailRepository orderDetailRepo;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private SessionService sessionService;

    // ====================================================
    // GET: Trang quản lý sản phẩm
    // ====================================================
    @GetMapping("/admin/products")
    public String listProducts(Model model,
            @RequestParam(value = "keyword", required = false) String keyword) {

        List<Product> all = productRepo.findAll();
        User currentUser = (User) sessionService.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);

        if (keyword != null && !keyword.trim().isEmpty()) {
            final String kw = removeAccent(keyword.trim());
            String[] words = kw.split("\\s+");
            all = all.stream().filter(p -> {
                String name = removeAccent(p.getName());
                return java.util.Arrays.stream(words).allMatch(name::contains);
            }).collect(Collectors.toList());
        }

        model.addAttribute("products",   all);
        model.addAttribute("categories", categoryRepo.findAll());
        model.addAttribute("authors",    authorRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("keyword",    keyword);
        return "admin-products";
    }

    public static String removeAccent(String s) {
        if (s == null) return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }

    // ====================================================
    // POST: Lưu sản phẩm (Thêm mới hoặc Cập nhật)
    // Sản phẩm mới tự động active = true
    // ====================================================
    @PostMapping("/admin/products/save")
@ResponseBody
public ResponseEntity<Map<String, Object>> save(
        @Valid @ModelAttribute ProductDTO dto,
        BindingResult result) {

    Map<String, Object> res = new HashMap<>();
    try {
        if (result.hasErrors()) {
            res.put("success", false);
            res.put("message", result.getFieldErrors().get(0).getDefaultMessage());
            return ResponseEntity.ok(res);
        }

        boolean isNew = (dto.getId() == null);
        if (isNew && (dto.getMainImage() == null || dto.getMainImage().isEmpty())) {
            res.put("success", false);
            res.put("message", "Vui lòng chọn ảnh bìa cho sản phẩm!");
            return ResponseEntity.ok(res);
        }

        Product product;
        if (!isNew) {
            product = productRepo.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + dto.getId()));
        } else {
            product = new Product();
            product.setSlug(SlugUtils.makeSlug(dto.getName()) + "-" + System.currentTimeMillis());
        }

        product.setName(dto.getName());
        product.setPublisher(dto.getPublisher());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setDescription(dto.getDescription());
        product.setCategory(categoryRepo.findById(dto.getCategoryId()).orElse(null));

        if (dto.getAuthorId() != null) {
            Author author = authorRepository.findById(dto.getAuthorId()).orElse(null);
            product.setAuthorEntity(author);
            product.setAuthor(author != null ? author.getName() : null);
        } else {
            product.setAuthorEntity(null);
            product.setAuthor(null);
        }

        if (isNew) product.setActive(true);

        if (dto.getMainImage() != null && !dto.getMainImage().isEmpty()) {
            String originalName = dto.getMainImage().getOriginalFilename();
            String extension = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf(".")) : "";
            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
            saveToImgFolder(dto.getMainImage(), fileName);
            product.setImage("/img/" + fileName);
        }

        Product savedProduct = productRepo.save(product);

        if (dto.getDetailImages() != null) {
            for (MultipartFile file : dto.getDetailImages()) {
                if (file != null && !file.isEmpty()) {
                    String originalName = file.getOriginalFilename();
                    String extension = (originalName != null && originalName.contains("."))
                            ? originalName.substring(originalName.lastIndexOf(".")) : "";
                    String detailName = System.currentTimeMillis() + extension;
                    saveToImgFolder(file, detailName);
                    Image imgEntity = new Image();
                    imgEntity.setName("/img/" + detailName);
                    imgEntity.setProduct(savedProduct);
                    productImageRepo.save(imgEntity);
                }
            }
        }

        res.put("success", true);
        res.put("message", isNew
                ? "Thêm sản phẩm \"" + savedProduct.getName() + "\" thành công!"
                : "Cập nhật sản phẩm \"" + savedProduct.getName() + "\" thành công!");

    } catch (Exception e) {
        res.put("success", false);
        res.put("message", "Lỗi hệ thống: " + e.getMessage());
    }
    return ResponseEntity.ok(res);
}
    // ====================================================
    // POST: Xóa sản phẩm (chỉ khi CHƯA có đơn hàng & chưa trong giỏ)
    // ====================================================
    @PostMapping("/admin/products/delete")
    public String delete(@RequestParam("id") Integer id, RedirectAttributes ra) {
        try {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + id));

            String productName = product.getName();
            boolean hasOrders  = product.getOrderDetails() != null && !product.getOrderDetails().isEmpty();
            boolean inCart     = product.getCartDetails()  != null && !product.getCartDetails().isEmpty();

            if (hasOrders || inCart) {
                ra.addFlashAttribute("errorMessage",
                        "Không thể xóa \"" + productName + "\" vì sản phẩm "
                        + (hasOrders ? "đã có trong đơn hàng" : "")
                        + (hasOrders && inCart ? " và " : "")
                        + (inCart ? "đang có trong giỏ hàng" : "")
                        + ". Vui lòng ẩn sản phẩm thay thế.");
                return "redirect:/admin/products";
            }

            productImageRepo.deleteAll(product.getImages());
            productRepo.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa sản phẩm \"" + productName + "\" thành công!");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ====================================================
    // POST: Ẩn / Hiện sản phẩm (toggle active)
    // ====================================================
    @PostMapping("/admin/products/toggle-active")
    public String toggleActive(
            @RequestParam("id") Integer id,
            RedirectAttributes ra) {
        try {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + id));

            boolean newActive = !Boolean.TRUE.equals(product.getActive());
            product.setActive(newActive);
            productRepo.save(product);

            ra.addFlashAttribute("successMessage",
                    newActive
                    ? "Đã hiển thị sản phẩm \"" + product.getName() + "\" trên cửa hàng."
                    : "Đã ẩn sản phẩm \"" + product.getName() + "\" khỏi cửa hàng.");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ====================================================
    // Hàm phụ: Lưu file ảnh
    // ====================================================
    private void saveToImgFolder(MultipartFile file, String fileName) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(fileName);
        file.transferTo(filePath.toFile());
    }
}