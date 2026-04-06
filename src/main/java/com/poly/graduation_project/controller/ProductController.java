package com.poly.graduation_project.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

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

    @Autowired
    private ImageRepository productImageRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private CategoryRepository categoryRepo;

    @Autowired
    private CartDetailRepository cartDetailRepo;

    @Autowired
    private OrderDetailRepository orderDetailRepo;
    @Autowired
    private SessionService sessionService;

    // ====================================================
    // GET: Trang quản lý sản phẩm
    // ====================================================
    @GetMapping("/admin/products")
    public String listProducts(Model model, @RequestParam(value = "keyword", required = false) String keyword) {
        List<Product> all = productRepo.findAll(); // Admin xem tất cả, kể cả ẩn
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

        model.addAttribute("products", all);
        model.addAttribute("categories", categoryRepo.findAll());
        model.addAttribute("keyword", keyword);
        return "admin-products";
    }

    public static String removeAccent(String s) {
        if (s == null)
            return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        return temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase();
    }

    // ====================================================
    // POST: Lưu sản phẩm (Thêm mới hoặc Cập nhật)
    // ====================================================
    @PostMapping("/admin/products/save")
    public String save(@Valid @ModelAttribute ProductDTO dto,
            BindingResult result,
            RedirectAttributes ra) {
        try {
            // Bắt lỗi @NotBlank trước
            if (result.hasErrors()) {
                String firstError = result.getFieldErrors().get(0).getDefaultMessage();
                ra.addFlashAttribute("errorMessage", firstError);
                return "redirect:/admin/products";
            }

            // Bắt lỗi chưa chọn ảnh bìa (chỉ khi THÊM MỚI)
            boolean isNew = (dto.getId() == null);
            if (isNew) {
                if (dto.getMainImage() == null || dto.getMainImage().isEmpty()) {
                    ra.addFlashAttribute("errorMessage", "Vui lòng chọn ảnh bìa cho sản phẩm!");
                    return "redirect:/admin/products";
                }
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
            product.setAuthor(dto.getAuthor());
            product.setPublisher(dto.getPublisher());
            product.setPrice(dto.getPrice());
            product.setStockQuantity(dto.getStockQuantity());
            product.setDescription(dto.getDescription());
            product.setCategory(categoryRepo.findById(dto.getCategoryId()).orElse(null));
            product.setActive(Boolean.TRUE.equals(dto.getActive()));

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
    // POST: Xóa sản phẩm
    // Chỉ cho xóa vĩnh viễn khi KHÔNG có đơn hàng và KHÔNG có trong giỏ hàng.
    // Nếu có → trả về lỗi (frontend đã xử lý chuyển sang modal ẩn/hết hàng).
    // ====================================================
    @PostMapping("/admin/products/delete")
    public String delete(@RequestParam("id") Integer id, RedirectAttributes ra) {
        try {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + id));

            String productName = product.getName();

            // Kiểm tra đã có trong đơn hàng chưa
            boolean hasOrders = product.getOrderDetails() != null
                    && !product.getOrderDetails().isEmpty();

            // Kiểm tra đang trong giỏ hàng của khách chưa
            boolean inCart = product.getCartDetails() != null
                    && !product.getCartDetails().isEmpty();

            if (hasOrders || inCart) {
                // Không cho xóa — frontend không nên gọi endpoint này trong trường hợp này,
                // nhưng vẫn guard ở backend để an toàn
                ra.addFlashAttribute("errorMessage",
                        "Không thể xóa \"" + productName + "\" vì sản phẩm "
                                + (hasOrders ? "đã có trong đơn hàng" : "")
                                + (hasOrders && inCart ? " và " : "")
                                + (inCart ? "đang có trong giỏ hàng" : "")
                                + ". Vui lòng ẩn hoặc đặt hết hàng thay thế.");
                return "redirect:/admin/products";
            }

            // Xóa ảnh gallery liên quan trước (tránh lỗi FK)
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
    // POST: Ẩn hoặc đặt hết hàng (dùng khi không thể xóa)
    // action = "outofstock" → stockQuantity = 0
    // action = "hide" → active = false
    // ====================================================
    @PostMapping("/admin/products/hide")
    public String hideOrOutOfStock(
            @RequestParam("id") Integer id,
            @RequestParam("action") String action,
            RedirectAttributes ra) {
        try {
            Product product = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID: " + id));

            String productName = product.getName();

            if ("outofstock".equals(action)) {
                product.setStockQuantity(0);
                productRepo.save(product);
                ra.addFlashAttribute("successMessage",
                        "Đã đặt sản phẩm \"" + productName + "\" về trạng thái hết hàng.");
            } else if ("hide".equals(action)) {
                product.setActive(false);
                productRepo.save(product);
                ra.addFlashAttribute("successMessage",
                        "Đã ẩn sản phẩm \"" + productName + "\" khỏi cửa hàng.");
            } else {
                ra.addFlashAttribute("errorMessage", "Hành động không hợp lệ!");
            }

        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ====================================================
    // Hàm phụ: Lưu file ảnh vào thư mục /img/
    // ====================================================
    private void saveToImgFolder(MultipartFile file, String fileName) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(fileName);
        file.transferTo(filePath.toFile());
    }
}