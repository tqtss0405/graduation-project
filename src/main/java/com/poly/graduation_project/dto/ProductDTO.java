package com.poly.graduation_project.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;

@Data
public class ProductDTO {

    private Integer id;

    @NotBlank(message = "Tên sách không được để trống")
    private String name;

    // authorId: null => tác giả ẩn danh
    private Integer authorId;

    @NotBlank(message = "Nhà xuất bản không được để trống")
    private String publisher;

    @NotNull(message = "Giá bán không được để trống")
    @Min(value = 0, message = "Giá bán không được âm")
    private BigDecimal price;

    @NotNull(message = "Số lượng kho không được để trống")
    @Min(value = 0, message = "Số lượng kho không được âm")
    private Long stockQuantity;

    @NotNull(message = "Vui lòng chọn danh mục")
    private Integer categoryId;

    private String description;

    private MultipartFile mainImage;
    private MultipartFile[] detailImages;
}