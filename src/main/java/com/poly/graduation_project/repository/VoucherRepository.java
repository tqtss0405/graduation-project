package com.poly.graduation_project.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.Voucher;

import jakarta.transaction.Transactional;

public interface VoucherRepository extends JpaRepository<Voucher, Integer> {
    boolean existsByCode(String code);

    // Kiểm tra mã đã tồn tại nhưng bỏ qua id hiện tại (dùng khi cập nhật)
    boolean existsByCodeAndIdNot(String code, Integer id);

    // Tự động set active = false cho các mã đã hết hạn
    @Modifying
    @Transactional
    @Query("UPDATE Voucher v SET v.active = false WHERE v.endAt < :now AND v.active = true")
    int disableExpiredVouchers(LocalDateTime now);

    // Tìm theo trạng thái
    List<Voucher> findByActive(Boolean active);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.voucher.id = :voucherId")
    long countUsedByVoucherId(@Param("voucherId") Integer voucherId);
}
