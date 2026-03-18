package com.poly.graduation_project.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;

    /**
     * Tự động chuyển đơn hàng "Đã giao" (status=3) sang "Hoàn thành" (status=4)
     * sau 7 ngày nếu khách chưa xác nhận.
     * Chạy mỗi giờ 1 lần.
     */
    @Scheduled(initialDelay = 10_000, fixedRate = 3_600_000)
    @Transactional
    public void autoCompleteDeliveredOrders() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        List<Order> orders = orderRepository.findByStatusOrderByCreateAtDesc(3);

        int count = 0;
        for (Order order : orders) {
            if (order.getCreateAt() != null && order.getCreateAt().isBefore(sevenDaysAgo)) {
                order.setStatus(4);
                orderRepository.save(order);
                count++;
            }
        }

        if (count > 0) {
            log.info("[Scheduler] Tự động hoàn thành {} đơn hàng đã giao quá 7 ngày.", count);
        }
    }
}