package com.poly.graduation_project.controller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.dto.ProductDTO;
import com.poly.graduation_project.helper.SlugUtils;
import com.poly.graduation_project.model.Image;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.repository.CategoryRepository;
import com.poly.graduation_project.repository.ImageRepository;
import com.poly.graduation_project.repository.ProductRepository;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.validation.Valid;

@MultipartConfig
@Controller
public class ProductController {
    @Value("${app.upload.dir}")
    private String uploadDir;
    @Autowired
    private ImageRepository productImageRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private CategoryRepository categoryRepo;

    // ====================================================
    // GET: Trang quản lý sản phẩm
    // ====================================================
    @GetMapping("/admin/products")
    public String listProducts(Model model) {
        model.addAttribute("products", productRepo.findAll());
        model.addAttribute("categories", categoryRepo.findAll());
        return "admin-products";
    }

    // ====================================================
    // POST: Lưu sản phẩm (Thêm mới hoặc Cập nhật)
    // Quy tắc bắt lỗi:
    // 1. Ưu tiên bắt lỗi @NotBlank trước (tên, tác giả, nhà XB)
    // 2. Sau đó bắt các lỗi @NotNull / @Min (giá, số lượng, danh mục)
    // 3. Cuối cùng bắt lỗi chưa chọn ảnh (chỉ khi thêm mới)
    // → Mỗi lần chỉ hiển thị đúng 1 dòng lỗi duy nhất
    // ====================================================
    @PostMapping("/admin/products/save")
    public String save(@Valid @ModelAttribute ProductDTO dto,
            BindingResult result,
            RedirectAttributes ra) {
        try {
            // --- Bước 1: Bắt lỗi @NotBlank trước (tên, tác giả, nhà xuất bản) ---
            if (result.hasErrors()) {
                // Ưu tiên lỗi blank trước các lỗi khác
                String blankError = result.getFieldErrors().stream()
                        .filter(e -> e.getCode() != null && e.getCode().equals("NotBlank"))
                        .map(FieldError::getDefaultMessage)
                        .findFirst()
                        .orElse(null);

                if (blankError != null) {
                    ra.addFlashAttribute("errorMessage", blankError);
                    return "redirect:/admin/products";
                }

                // Nếu không có lỗi blank → lấy lỗi đầu tiên còn lại (NotNull, Min...)
                String firstError = result.getFieldErrors().get(0).getDefaultMessage();
                ra.addFlashAttribute("errorMessage", firstError);
                return "redirect:/admin/products";
            }

            // --- Bước 2: Bắt lỗi chưa chọn ảnh bìa (chỉ khi THÊM MỚI) ---
            boolean isNew = (dto.getId() == null);
            if (isNew) {
                if (dto.getMainImage() == null || dto.getMainImage().isEmpty()) {
                    ra.addFlashAttribute("errorMessage", "Vui lòng chọn ảnh bìa cho sản phẩm!");
                    return "redirect:/admin/products";
                }
            }

            // --- Bước 3: Xử lý lưu Product entity ---
            Product product;
            if (!isNew) {
                // Cập nhật: tìm sản phẩm cũ theo ID
                product = productRepo.findById(dto.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + dto.getId()));
            } else {
                // Thêm mới: tạo slug duy nhất
                product = new Product();
                product.setSlug(SlugUtils.makeSlug(dto.getName()) + "-" + System.currentTimeMillis());
            }

            product.setName(dto.getName());
            product.setAuthor(dto.getAuthor());
            product.setPublisher(dto.getPublisher());
            product.setPrice(dto.getPrice());
            product.setStockQuantity(dto.getStockQuantity());
            product.setDescription(dto.getDescription());
            product.setCategory(categoryRepo.findById(dto.getCategoryId()).orElse(null));
            product.setActive(dto.getActive() != null ? dto.getActive() : true);

            // Xử lý ảnh bìa chính (nếu có chọn ảnh mới thì thay thế, không thì giữ cũ)
            if (dto.getMainImage() != null && !dto.getMainImage().isEmpty()) {
                String originalName = dto.getMainImage().getOriginalFilename();
                String extension = "";

                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(originalName.lastIndexOf("."));
                }
                String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
                saveToImgFolder(dto.getMainImage(), fileName);
                product.setImage("/img/" + fileName);
            }

            Product savedProduct = productRepo.save(product);

            // Xử lý lưu ảnh gallery (nếu có chọn ảnh mới)
            if (dto.getDetailImages() != null) {
                for (MultipartFile file : dto.getDetailImages()) {
                    if (file != null && !file.isEmpty()) {
                        String originalName = file.getOriginalFilename();
                        String extension = "";

                        if (originalName != null && originalName.contains(".")) {
                            extension = originalName.substring(originalName.lastIndexOf("."));
                        }
                        String detailName = System.currentTimeMillis() + extension;
                        saveToImgFolder(file, detailName);
                        Image imgEntity = new Image();
                        imgEntity.setName("/img/" + detailName);
                        imgEntity.setProduct(savedProduct);
                        productImageRepo.save(imgEntity);
                    }
                }
            }

            ra.addFlashAttribute("successMessage",
                    isNew ? "Thêm sản phẩm \"" + savedProduct.getName() + "\" thành công!"
                            : "Cập nhật sản phẩm \"" + savedProduct.getName() + "\" thành công!");

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ====================================================
    // POST: Xóa sản phẩm theo ID
    // ====================================================
    @PostMapping("/admin/products/delete")
    public String delete(@RequestParam("id") Integer id,
            RedirectAttributes ra) {
        try {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + id));

            String productName = product.getName();

            // Xóa các ảnh gallery liên quan trước (tránh lỗi FK)
            productImageRepo.deleteAll(product.getImages());

            // Xóa sản phẩm
            productRepo.deleteById(id);

            ra.addFlashAttribute("successMessage", "Đã xóa sản phẩm \"" + productName + "\" thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ====================================================
    // Hàm phụ: Lưu file ảnh vào thư mục /img/ trên server
    // ====================================================
  private void saveToImgFolder(MultipartFile file, String fileName) throws IOException {

    Path uploadPath = Paths.get(uploadDir);

    // Tự động tạo folder nếu chưa tồn tại
    Files.createDirectories(uploadPath);

    Path filePath = uploadPath.resolve(fileName);

    file.transferTo(filePath.toFile());
}
}