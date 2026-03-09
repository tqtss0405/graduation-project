package com.poly.graduation_project.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.poly.graduation_project.config.VNPayConfig;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VNPayUtil {

    private final VNPayConfig vnPayConfig;

    /**
     * Tạo URL thanh toán VNPay
     * 
     * @param orderId   ID đơn hàng tạm (lưu vào session)
     * @param amount    Tổng tiền (VND, không có phần thập phân)
     * @param orderInfo Mô tả đơn hàng
     * @param ipAddr    IP khách hàng
     * @return URL redirect đến cổng VNPay
     */
    public String createPaymentUrl(String orderId, long amount, String orderInfo, String ipAddr) {
        String vnp_Version = vnPayConfig.getVersion();
        String vnp_Command = vnPayConfig.getCommand();
        String vnp_TmnCode = vnPayConfig.getTmnCode();
        String vnp_CurrCode = vnPayConfig.getCurrCode();
        String vnp_Locale = vnPayConfig.getLocale();
        String vnp_OrderType = vnPayConfig.getOrderType();
        String vnp_ReturnUrl = vnPayConfig.getReturnUrl();

        // Thời gian tạo và hết hạn
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());

        // Tham số giao dịch
        Map<String, String> vnp_Params = new TreeMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VNPay nhân 100
        vnp_Params.put("vnp_CurrCode", vnp_CurrCode);
        vnp_Params.put("vnp_TxnRef", orderId);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", vnp_OrderType);
        vnp_Params.put("vnp_Locale", vnp_Locale);
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", ipAddr);
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Build query string
        // Build query string và hashData
        List<String> hashList = new ArrayList<>();
        List<String> queryList = new ArrayList<>();

        for (Map.Entry<String, String> entry : vnp_Params.entrySet()) {
            // hashData: key=value (KHÔNG encode, dùng để ký)
            hashList.add(entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            // query: key=value (encode cả key, dùng để gửi lên URL)
            queryList.add(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }

        String hashData = String.join("&", hashList);
        String query = String.join("&", queryList);

        String vnp_SecureHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData);
        String paymentUrl = vnPayConfig.getPayUrl() + "?" + query + "&vnp_SecureHash=" + vnp_SecureHash;

        return paymentUrl;
    }

    /**
     * Xác minh chữ ký VNPay trả về
     */
    public boolean verifySignature(Map<String, String> params) {
        String vnp_SecureHash = params.get("vnp_SecureHash");
        if (vnp_SecureHash == null)
            return false;

        // Loại bỏ các tham số chữ ký
        Map<String, String> signParams = new TreeMap<>(params);
        signParams.remove("vnp_SecureHash");
        signParams.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : signParams.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                hashData.append(entry.getKey()).append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                        .append('&');
            }
        }
        if (hashData.length() > 0)
            hashData.deleteCharAt(hashData.length() - 1);

        String calculatedHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        return calculatedHash.equalsIgnoreCase(vnp_SecureHash);
    }

    /**
     * Lấy IP thực của client
     */
    public String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo HMAC SHA512", e);
        }
    }
}