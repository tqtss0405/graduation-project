package com.poly.graduation_project.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.poly.graduation_project.model.Voucher;

import jakarta.transaction.Transactional;

public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);

    // Tìm voucher theo code (dùng khi apply voucher ở checkout)
    Optional<Voucher> findByCode(String code);

    @Modifying
    @Transactional
    @Query("UPDATE Voucher v SET v.active = false WHERE v.endAt < :now AND v.active = true")
    int disableExpiredVouchers(LocalDateTime now);

    List<Voucher> findByActive(Boolean active);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.voucher.id = :voucherId")
    long countUsedByVoucherId(@Param("voucherId") Integer voucherId);
}