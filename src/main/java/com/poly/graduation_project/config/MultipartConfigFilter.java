package com.poly.graduation_project.config;

import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebFilter;

import java.io.IOException;

import org.springframework.stereotype.Component;

@Component
@WebFilter(urlPatterns = "/*")
@MultipartConfig(
    maxFileSize      = 50 * 1024 * 1024,  // 50MB mỗi file
    maxRequestSize   = 200 * 1024 * 1024, // 200MB toàn request
    fileSizeThreshold = 0
)
public class MultipartConfigFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, response);
    }
}