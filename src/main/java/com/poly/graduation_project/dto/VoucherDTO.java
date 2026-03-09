package com.poly.graduation_project.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoucherDTO {

    private Integer id;

    @NotBlank(message = "Mã giảm giá không được để trống")
    @Size(max = 10, message = "Mã giảm giá tối đa 10 ký tự")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Mã chỉ chứa chữ hoa và số")
    private String code;

    @NotBlank(message = "Tên chương trình không được để trống")
    @Size(max = 250, message = "Tên tối đa 250 ký tự")
    private String name;

    @NotNull(message = "Giá trị giảm không được để trống")
    @Min(value = 1, message = "Giá trị giảm phải lớn hơn 0")
    @Max(value = 100, message = "Giá trị giảm không được vượt quá 100")
    private Integer discount;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantity;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @NotNull(message = "Ngày hết hạn không được để trống")
    private LocalDateTime endAt;

    private Boolean active;

    public boolean isEndAfterStart() {
        if (startedAt == null || endAt == null)
            return true;
        return endAt.isAfter(startedAt);
    }
}