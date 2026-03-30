package com.poly.graduation_project.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.poly.graduation_project.model.Address;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.AddressRepository;
import com.poly.graduation_project.service.AddressService;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user/profile/address")
public class AddressController {

    @Autowired
    AddressService addressService;

    @Autowired
    AddressRepository addressRepository;

    // ──────────────────────────────────────────────────────────────
    // Thêm địa chỉ — dùng @RequestParam để tránh lỗi DomainClassConverter
    // ──────────────────────────────────────────────────────────────
    @PostMapping("/add")
    public String addAddress(
            @RequestParam("provinceId")  String provinceId,
            @RequestParam("districtId")  String districtId,
            @RequestParam("wardcode")    String wardcode,
            @RequestParam("address")     String addressStr,
            @RequestParam("fulladdress") String fulladdress,
            @RequestParam(value = "isDefault", required = false) List<String> isDefaultValues,
            HttpSession session) {

        boolean isDefault = isDefaultValues != null
            && isDefaultValues.stream().anyMatch(v -> "true".equalsIgnoreCase(v));

        User user = (User) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultAddress(user);
        }

        Address addr = new Address();
        addr.setUser(user);
        addr.setProvinceId(provinceId);
        addr.setDistrictId(districtId);
        addr.setWardcode(wardcode);
        addr.setAddress(addressStr);
        addr.setFulladdress(fulladdress);
        addr.setIsDefault(isDefault);
        addr.setActive(true);

        addressService.save(addr);
        return "redirect:/user/profile";
    }

    // ──────────────────────────────────────────────────────────────
    // Lấy thông tin địa chỉ (AJAX — cho modal sửa)
    // ──────────────────────────────────────────────────────────────
    @GetMapping("/get/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAddress(
            @PathVariable Integer id, HttpSession session) {

        User user = (User) session.getAttribute("currentUser");
        Map<String, Object> res = new HashMap<>();

        if (user == null) {
            res.put("success", false);
            return ResponseEntity.ok(res);
        }

        Address addr = addressRepository.findById(id).orElse(null);
        if (addr == null || !addr.getUser().getId().equals(user.getId())) {
            res.put("success", false);
            return ResponseEntity.ok(res);
        }

        res.put("success",     true);
        res.put("id",          addr.getId());
        res.put("provinceId",  addr.getProvinceId());
        res.put("districtId",  addr.getDistrictId());
        res.put("wardcode",    addr.getWardcode());
        res.put("address",     addr.getAddress());
        res.put("fulladdress", addr.getFulladdress());
        res.put("isDefault",   Boolean.TRUE.equals(addr.getIsDefault()));
        return ResponseEntity.ok(res);
    }

    // ──────────────────────────────────────────────────────────────
    // Sửa địa chỉ — cũng dùng @RequestParam
    // ──────────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String updateAddress(
            @PathVariable Integer id,
            @RequestParam("provinceId")  String provinceId,
            @RequestParam("districtId")  String districtId,
            @RequestParam("wardcode")    String wardcode,
            @RequestParam("address")     String addressStr,
            @RequestParam("fulladdress") String fulladdress,
            @RequestParam(value = "isDefault", required = false) List<String> isDefaultValues,
            HttpSession session) {

        boolean isDefault = isDefaultValues != null
            && isDefaultValues.stream().anyMatch(v -> "true".equalsIgnoreCase(v));

        User user = (User) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Address addr = addressRepository.findById(id).orElse(null);
        if (addr == null || !addr.getUser().getId().equals(user.getId()))
            return "redirect:/user/profile";

        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultAddress(user);
        }

        addr.setProvinceId(provinceId);
        addr.setDistrictId(districtId);
        addr.setWardcode(wardcode);
        addr.setAddress(addressStr);
        addr.setFulladdress(fulladdress);
        addr.setIsDefault(isDefault);
        addressRepository.save(addr);

        return "redirect:/user/profile";
    }

    // ──────────────────────────────────────────────────────────────
    // Đặt địa chỉ mặc định
    // ──────────────────────────────────────────────────────────────
    @RequestMapping(value = "/set-default/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    public String setDefault(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Address addr = addressRepository.findById(id).orElse(null);
        if (addr != null
                && Boolean.TRUE.equals(addr.getActive())
                && addr.getUser().getId().equals(user.getId())) {
            clearDefaultAddress(user);
            addr.setIsDefault(true);
            addressRepository.save(addr);
        }
        return "redirect:/user/profile";
    }

    // ──────────────────────────────────────────────────────────────
    // Xóa địa chỉ (soft delete)
    // ──────────────────────────────────────────────────────────────
    @GetMapping("/delete/{id}")
    public String deleteAddress(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currentUser");
        Address addr = addressRepository.findById(id).orElse(null);
        if (addr != null && user != null && addr.getUser().getId().equals(user.getId())) {
            addressService.delete(id);
        }
        return "redirect:/user/profile";
    }

    // ──────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────
    private void clearDefaultAddress(User user) {
        List<Address> existing = addressRepository.findByUser(user);
        for (Address a : existing) {
            if (Boolean.TRUE.equals(a.getIsDefault())) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        }
    }
}