package com.poly.graduation_project.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;



/**
 * Controller gọi API GHN để tính phí vận chuyển động.
 * Endpoint: POST /api/shipping-fee
 */
@RestController
@RequestMapping("/api")
public class ShippingController {

    // ── Cấu hình GHN (đặt trong application.properties hoặc application.yml) ──
    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Value("${ghn.base-url}")
    private String ghnBaseUrl;

    @Value("${ghn.from-district-id}")
    private Integer fromDistrictId;

    @Value("${ghn.from-ward-code}")
    private String fromWardCode;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tính phí ship GHN
     *
     * @param body JSON: { "toDistrictId": 1442, "toWardCode": "21211" }
     * @return JSON: { "success": true, "fee": 35000 }
     */
    @PostMapping("/shipping-fee")
    public ResponseEntity<Map<String, Object>> calculateShippingFee(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new HashMap<>();

        try {
            Integer toDistrictId = (Integer) body.get("toDistrictId");
            String toWardCode = (String) body.get("toWardCode");

            if (toDistrictId == null || toWardCode == null || toWardCode.isBlank()) {
                result.put("success", false);
                result.put("message", "Thiếu thông tin quận/huyện hoặc phường/xã!");
                return ResponseEntity.ok(result);
            }

            // Payload gửi lên GHN
            Map<String, Object> payload = new HashMap<>();
            payload.put("service_type_id", 2);           // Giao hàng thường
            payload.put("from_district_id", fromDistrictId);
            payload.put("from_ward_code", fromWardCode);
            payload.put("to_district_id", toDistrictId);
            payload.put("to_ward_code", toWardCode);
            payload.put("weight", 500);                  // 500g mặc định mỗi đơn
            payload.put("length", 30);
            payload.put("width", 20);
            payload.put("height", 5);
            payload.put("insurance_value", 0);
            payload.put("coupon", "");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Token", ghnToken);
            headers.set("ShopId", ghnShopId);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    ghnBaseUrl + "/v2/shipping-order/fee",
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.path("code").asInt() == 200) {
                long fee = root.path("data").path("total").asLong();
                result.put("success", true);
                result.put("fee", fee);
            } else {
                String msg = root.path("message").asText("Không tính được phí ship!");
                result.put("success", false);
                result.put("message", msg);
                // Fallback về phí cố định
                result.put("fee", 30000L);
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Lỗi kết nối GHN: " + e.getMessage());
            // Fallback để không block checkout
            result.put("fee", 30000L);
        }

        return ResponseEntity.ok(result);
    }
}