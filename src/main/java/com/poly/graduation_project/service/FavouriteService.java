package com.poly.graduation_project.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.poly.graduation_project.model.Favourite;
import com.poly.graduation_project.model.Product;
import com.poly.graduation_project.model.User;
import com.poly.graduation_project.repository.FavouriteRepository;
import com.poly.graduation_project.repository.ProductRepository;

@Service
public class FavouriteService {

    @Autowired
    private FavouriteRepository favouriteRepository;

    @Autowired
    private ProductRepository productRepository;

    // Lấy danh sách yêu thích của user
    public List<Favourite> getByUser(User user) {
        return favouriteRepository.findByUser(user);
    }

    // Kiểm tra đã thích chưa
    public boolean isFavourite(User user, Product product) {
        return favouriteRepository.existsByUserAndProduct(user, product);
    }

    // Toggle: thêm nếu chưa thích, xóa nếu đã thích
    // Trả về true = vừa thêm, false = vừa bỏ
    @Transactional
    public boolean toggleFavourite(User user, Integer productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null)
            return false;

        if (favouriteRepository.existsByUserAndProduct(user, product)) {
            // ✅ Dùng @Query JPQL delete thay vì derived delete
            favouriteRepository.deleteByUserAndProduct(user, product);
            return false; // đã bỏ thích
        } else {
            Favourite fav = new Favourite();
            fav.setUser(user);
            fav.setProduct(product);
            favouriteRepository.save(fav);
            return true; // đã thêm thích
        }
    }

    // Xóa theo id bản ghi Favourite (dùng ở trang danh sách yêu thích)
    @Transactional
    public void removeById(Integer favouriteId, User user) {
        Favourite fav = favouriteRepository.findById(favouriteId).orElse(null);
        if (fav != null && fav.getUser().getId().equals(user.getId())) {
            favouriteRepository.deleteById(favouriteId);
        }
    }
}