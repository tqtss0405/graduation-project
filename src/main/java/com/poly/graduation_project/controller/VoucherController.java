package com.poly.graduation_project.controller;

import java.time.LocalDateTime; 
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.poly.graduation_project.dto.VoucherDTO;
import com.poly.graduation_project.model.Voucher;
import com.poly.graduation_project.service.VoucherService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // Hiển thị danh sách
   @GetMapping("/admin/vouchers")
public String vouchers(Model model) {
    List<Voucher> all = voucherService.getAll();
    LocalDateTime now = LocalDateTime.now();

    long total    = all.size();
    long active   = all.stream().filter(v -> Boolean.TRUE.equals(v.getActive())).count();
    long expired  = all.stream().filter(v -> v.getEndAt() != null && v.getEndAt().isBefore(now)).count();
    long upcoming = all.stream().filter(v -> v.getStartedAt() != null && v.getStartedAt().isAfter(now)).count();

    // Đếm số lần đã dùng cho từng voucher
    Map<Integer, Long> usedCountMap = voucherService.getUsedCountMap(all);

    model.addAttribute("vouchers", all);
    model.addAttribute("usedCountMap", usedCountMap);
    model.addAttribute("totalVouchers",   total);
    model.addAttribute("activeVouchers",  active);
    model.addAttribute("expiredVouchers", expired);
    model.addAttribute("upcomingVouchers",upcoming);
    model.addAttribute("voucherDTO", new VoucherDTO());
    return "admin-vouchers";
}

    // Tạo mới
    @PostMapping("/admin/vouchers/save")
    public String create(@Valid @ModelAttribute("voucherDTO") VoucherDTO dto,
            BindingResult result,
            RedirectAttributes ra,
            Model model) {

        if (!dto.isEndAfterStart()) {
            result.rejectValue("endAt", "date.invalid", "Ngày hết hạn phải sau ngày bắt đầu");
        }

      if (result.hasErrors()) {
    boolean hasBlank = result.getFieldErrors().stream()
            .anyMatch(e -> e.getCode().equals("NotBlank") || e.getCode().equals("NotNull"));

    String errorMsg = hasBlank
            ? "Vui lòng nhập đầy đủ thông tin"
            : result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(" • "));

    ra.addFlashAttribute("errorMsg", errorMsg);
    return "redirect:/admin/vouchers";
}

        try {
            voucherService.create(dto);
            ra.addFlashAttribute("successMsg", "Tạo mã giảm giá thành công!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    // Load data sửa (trả JSON cho modal)
    @GetMapping("/admin/vouchers/edit/{id}")
    @ResponseBody
    public VoucherDTO getForEdit(@PathVariable Integer id) {
        Voucher v = voucherService.getById(id);
        VoucherDTO dto = new VoucherDTO();
        dto.setId(v.getId());
        dto.setCode(v.getCode());
        dto.setName(v.getName());
        dto.setDiscount(v.getDiscount());
        dto.setQuantity(v.getQuantity());
        dto.setStartedAt(v.getStartedAt());
        dto.setEndAt(v.getEndAt());
        dto.setActive(v.getActive());
        return dto;
    }

    // Cập nhật
    @PostMapping("/admin/vouchers/update/{id}")
    public String update(@PathVariable Integer id,
            @Valid @ModelAttribute("editDTO") VoucherDTO dto,
            BindingResult result,
            RedirectAttributes ra,
            Model model) {

        if (!dto.isEndAfterStart()) {
            result.rejectValue("endAt", "date.invalid", "Ngày hết hạn phải sau ngày bắt đầu");
        }

    if (result.hasErrors()) {
    boolean hasBlank = result.getFieldErrors().stream()
            .anyMatch(e -> e.getCode().equals("NotBlank") || e.getCode().equals("NotNull"));

    String errorMsg = hasBlank
            ? "Vui lòng nhập đầy đủ thông tin"
            : result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(" • "));

    ra.addFlashAttribute("errorMsg", errorMsg);
    return "redirect:/admin/vouchers";
}

        try {
            voucherService.update(id, dto);
            ra.addFlashAttribute("successMsg", "Cập nhật thành công!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    // Xóa
    @PostMapping("/admin/vouchers/delete/{id}")
    public String delete(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            voucherService.delete(id);
            ra.addFlashAttribute("successMsg", "Đã xóa mã giảm giá!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Không thể xóa: " + e.getMessage());
        }
        return "redirect:/admin/vouchers";
    }
}