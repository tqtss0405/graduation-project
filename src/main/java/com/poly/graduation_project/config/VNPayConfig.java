package com.poly.graduation_project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VNPayConfig {
    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String returnUrl;
    private String version;
    private String command;
    private String currCode;
    private String locale;
    private String orderType;
}