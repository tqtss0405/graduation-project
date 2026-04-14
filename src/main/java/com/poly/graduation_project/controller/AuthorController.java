package com.poly.graduation_project.controller;

import com.poly.graduation_project.model.Author;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.AuthorRepository;
import com.poly.graduation_project.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/authors")
public class AuthorController {

    @Autowired
    private AuthorRepository authorRepository;
    @Autowired
    private SessionService sessionService;

    // ── GET: Trang quản lý tác giả ────────────────────────────────────────
    @GetMapping
    public String authorsPage(Model model) {
        User currentUser = (User) sessionService.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);
        return "admin-authors";
    }

    // ── AJAX: Tìm kiếm tác giả active (dùng trong picker thêm sách) ───────
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<Author>> search(
            @RequestParam(value = "kw", defaultValue = "") String kw) {
        List<Author> results = kw.trim().isEmpty()
                ? authorRepository.findByActiveTrueOrderByNameAsc()
                : authorRepository.searchByName(kw.trim());
        return ResponseEntity.ok(results);
    }

    // ── AJAX: Tất cả tác giả active ───────────────────────────────────────
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<Author>> listActive() {
        return ResponseEntity.ok(authorRepository.findByActiveTrueOrderByNameAsc());
    }

    // ── AJAX: Tất cả tác giả kể cả ẩn (trang quản lý) ────────────────────
    @GetMapping("/all")
    @ResponseBody
    public ResponseEntity<List<Author>> listAll() {
        return ResponseEntity.ok(authorRepository.findAllWithProducts());
    }

    // ── AJAX: Thêm mới ────────────────────────────────────────────────────
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam("name") String name,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "nationality", required = false) String nationality) {

        Map<String, Object> res = new HashMap<>();
        name = name == null ? "" : name.trim();

        if (name.isEmpty()) {
            res.put("success", false);
            res.put("message", "Tên tác giả không được để trống!");
            return ResponseEntity.ok(res);
        }
        if (authorRepository.existsByNameIgnoreCase(name)) {
            res.put("success", false);
            res.put("message", "Tên tác giả \"" + name + "\" đã tồn tại!");
            return ResponseEntity.ok(res);
        }

        Author a = new Author();
        a.setName(name);
        a.setBio(bio != null && !bio.isBlank() ? bio.trim() : null);
        a.setNationality(nationality != null && !nationality.isBlank() ? nationality.trim() : null);
        a.setActive(true);
        Author saved = authorRepository.save(a);

        res.put("success", true);
        res.put("message", "Thêm tác giả \"" + saved.getName() + "\" thành công!");
        res.put("id", saved.getId());
        res.put("name", saved.getName());
        return ResponseEntity.ok(res);
    }

    // ── AJAX: Cập nhật ────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Integer id,
            @RequestParam("name") String name,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "nationality", required = false) String nationality) {

        Map<String, Object> res = new HashMap<>();
        name = name == null ? "" : name.trim();

        if (name.isEmpty()) {
            res.put("success", false);
            res.put("message", "Tên tác giả không được để trống!");
            return ResponseEntity.ok(res);
        }
        Author a = authorRepository.findById(id).orElse(null);
        if (a == null) {
            res.put("success", false);
            res.put("message", "Không tìm thấy tác giả #" + id);
            return ResponseEntity.ok(res);
        }
        if (authorRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            res.put("success", false);
            res.put("message", "Tên tác giả \"" + name + "\" đã tồn tại!");
            return ResponseEntity.ok(res);
        }

        a.setName(name);
        a.setBio(bio != null && !bio.isBlank() ? bio.trim() : null);
        a.setNationality(nationality != null && !nationality.isBlank() ? nationality.trim() : null);
        authorRepository.save(a);

        res.put("success", true);
        res.put("message", "Cập nhật tác giả \"" + a.getName() + "\" thành công!");
        res.put("id", a.getId());
        res.put("name", a.getName());
        return ResponseEntity.ok(res);
    }

    // ── AJAX: Xóa hoặc Ẩn ────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Integer id) {
        Map<String, Object> res = new HashMap<>();
        Author a = authorRepository.findById(id).orElse(null);
        if (a == null) {
            res.put("success", false);
            res.put("message", "Không tìm thấy tác giả!");
            return ResponseEntity.ok(res);
        }

        long productCount = authorRepository.countProductsByAuthorId(id); // ← thay thế getProducts()
        if (productCount > 0) {
            a.setActive(false);
            authorRepository.save(a);
            res.put("success", true);
            res.put("action", "hidden");
            res.put("message",
                    "Đã ẩn tác giả \"" + a.getName() + "\" (đang liên kết với " + productCount + " sản phẩm).");
        } else {
            authorRepository.delete(a);
            res.put("success", true);
            res.put("action", "deleted");
            res.put("message", "Đã xóa vĩnh viễn tác giả \"" + a.getName() + "\".");
        }
        return ResponseEntity.ok(res);
    }

    // ── AJAX: Khôi phục ───────────────────────────────────────────────────
    @PostMapping("/restore/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Integer id) {
        Map<String, Object> res = new HashMap<>();
        Author a = authorRepository.findById(id).orElse(null);
        if (a == null) {
            res.put("success", false);
            res.put("message", "Không tìm thấy tác giả!");
            return ResponseEntity.ok(res);
        }
        a.setActive(true);
        authorRepository.save(a);
        res.put("success", true);
        res.put("message", "Đã khôi phục tác giả \"" + a.getName() + "\".");
        return ResponseEntity.ok(res);
    }
}