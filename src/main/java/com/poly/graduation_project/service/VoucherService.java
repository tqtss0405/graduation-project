package com.poly.graduation_project.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.poly.graduation_project.dto.VoucherDTO;
import com.poly.graduation_project.model.Voucher;
import com.poly.graduation_project.repository.VoucherRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    @Transactional(readOnly = true)
    public List<Voucher> getAll() {
        return voucherRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Voucher getById(Integer id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy voucher với id: " + id));
    }

    public Voucher create(VoucherDTO dto) {
        if (!dto.isEndAfterStart())
            throw new IllegalArgumentException("Ngày hết hạn phải sau ngày bắt đầu");
        if (voucherRepository.existsByCode(dto.getCode()))
            throw new IllegalArgumentException("Mã '" + dto.getCode() + "' đã tồn tại");

        Voucher v = new Voucher();
        mapDtoToEntity(dto, v, true); // isNew = true → luôn active
        v.setCreatedAt(LocalDateTime.now());
        return voucherRepository.save(v);
    }

    public Voucher update(Integer id, VoucherDTO dto) {
        Voucher v = getById(id);
        if (!dto.isEndAfterStart())
            throw new IllegalArgumentException("Ngày hết hạn phải sau ngày bắt đầu");
        if (voucherRepository.existsByCodeAndIdNot(dto.getCode(), id))
            throw new IllegalArgumentException("Mã '" + dto.getCode() + "' đã được sử dụng");

        mapDtoToEntity(dto, v, false); // isNew = false → giữ logic active của admin
        return voucherRepository.save(v);
    }

    public Voucher save(Voucher voucher) {
        return voucherRepository.save(voucher);
    }

    public void delete(Integer id) {
        voucherRepository.delete(getById(id));
    }

    @Scheduled(initialDelay = 1_000, fixedRate = 3_600_000)
    public void autoDisableExpiredVouchers() {
        int count = voucherRepository.disableExpiredVouchers(LocalDateTime.now());
        if (count > 0) {
            log.info("[Scheduler] Đã tự động disable {} voucher hết hạn.", count);
        }
    }

    /**
     * Map DTO → entity.
     *
     * @param isNew khi tạo mới → active = true (trừ khi endAt đã qua)
     *              khi cập nhật → admin quyết định qua dto.active
     */
    private void mapDtoToEntity(VoucherDTO dto, Voucher v, boolean isNew) {
        v.setCode(dto.getCode().toUpperCase());
        v.setName(dto.getName());
        v.setDiscount(dto.getDiscount());
        v.setQuantity(dto.getQuantity());
        v.setStartedAt(dto.getStartedAt());
        v.setEndAt(dto.getEndAt());

        boolean expired = dto.getEndAt() != null && dto.getEndAt().isBefore(LocalDateTime.now());

        if (isNew) {
            // Khi tạo mới: luôn active, trừ khi ngày hết hạn đã qua
            v.setActive(!expired);
        } else {
            // Khi cập nhật: nếu đã hết hạn thì bắt buộc false, ngược lại theo ý admin
            v.setActive(expired ? false : (dto.getActive() != null ? dto.getActive() : true));
        }
    }

    public Map<Integer, Long> getUsedCountMap(List<Voucher> vouchers) {
        Map<Integer, Long> map = new java.util.HashMap<>();
        for (Voucher v : vouchers) {
            map.put(v.getId(), voucherRepository.countUsedByVoucherId(v.getId()));
        }
        return map;
    }
}