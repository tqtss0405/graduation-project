package com.poly.graduation_project.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/ghn")
public class GhnAddressController {

    @Value("${ghn.token}")
    private String ghnToken;

    // Dùng riêng URL master-data — KHÔNG lấy từ ghn.base-url
    // vì base-url thường trỏ tới v2/shipping-order
    private static final String MASTER_DATA_URL =
            "https://online-gateway.ghn.vn/shiip/public-api/master-data";

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/provinces")
    public ResponseEntity<String> getProvinces() {
        return callGhn(MASTER_DATA_URL + "/province");
    }

    @GetMapping("/districts")
    public ResponseEntity<String> getDistricts(@RequestParam Integer provinceId) {
        return callGhn(MASTER_DATA_URL + "/district?province_id=" + provinceId);
    }

    @GetMapping("/wards")
    public ResponseEntity<String> getWards(@RequestParam Integer districtId) {
        return callGhn(MASTER_DATA_URL + "/ward?district_id=" + districtId);
    }

    private ResponseEntity<String> callGhn(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("{\"code\":500,\"message\":\"" + e.getMessage() + "\",\"data\":null}");
        }
    }
}