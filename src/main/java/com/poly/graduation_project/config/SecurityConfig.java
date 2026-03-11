package com.poly.graduation_project.config;

import com.poly.graduation_project.repository.UserRepository;
import com.poly.graduation_project.service.CustomOAuth2UserService;
import com.poly.graduation_project.service.UserService;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
        @Autowired
        private UserService userService;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private CustomOAuth2UserService customOAuth2UserService;
        @Autowired
        private PasswordEncoder passwordEncoder;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                // 1. Cấu hình CSRF và CORS
                http.csrf(c -> c.disable())
                                .cors(c -> c.disable());

                // 2. Cấu hình quyền truy cập (Đây là chỗ CHECK quyền)
                http.authorizeHttpRequests(auth -> auth
                                .requestMatchers("/admin/**").hasRole("ADMIN") // Bắt buộc login + Role ADMIN
                                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN") // Bắt buộc login + Role USER
                                                                                         // hoặc ADMIN
                                .requestMatchers("/assets/**", "/js/**", "/css/**").permitAll() // Cho phép tài nguyên
                                                                                                // tĩnh
                                .requestMatchers("/", "/login/**", "/register").permitAll() // Cho phép trang chủ, login
                                .anyRequest().permitAll() // Các link còn lại cho phép hết (hoặc dùng .authenticated()
                                                          // nếu muốn chặn
                                                          // hết)
                );
                // 3. Cấu hình đăng nhập mạng xã hội (Google/Facebook)
                http.oauth2Login(oauth2 -> oauth2
                                .loginPage("/login/form")
                                .defaultSuccessUrl("/login/success", true)
                                .failureUrl("/login/failure")
                                .userInfoEndpoint(userInfo -> userInfo
                                                .userService(customOAuth2UserService) // Sử dụng Service vừa tạo
                                )
                                .successHandler((request, response, authentication) -> {
                                        DefaultOidcUser oidcUser = (DefaultOidcUser) authentication.getPrincipal();
                                        String email = oidcUser.getEmail();
                                        String fullname = oidcUser.getFullName();
                                        String avatarUrl = oidcUser.getPicture();

                                        com.poly.graduation_project.model.User user = userRepository.findByEmail(email)
                                                        .orElseGet(() -> {
                                                                com.poly.graduation_project.model.User newUser = new com.poly.graduation_project.model.User();
                                                                newUser.setEmail(email);
                                                                newUser.setFullname(fullname);
                                                                String randomPassword = UUID.randomUUID().toString();
                                                                newUser.setPassword(
                                                                                passwordEncoder.encode(randomPassword));
                                                                newUser.setImage(avatarUrl);
                                                                newUser.setRole(false);
                                                                newUser.setActive(true);
                                                                return userRepository.save(newUser);
                                                        });

                                        // ✅ THÊM: Kiểm tra tài khoản có bị chặn không
                                        if (Boolean.FALSE.equals(user.getActive())) {
                                                SecurityContextHolder.clearContext();
                                                request.getSession().invalidate();
                                                response.sendRedirect("/login/form?blocked=true");
                                                return;
                                        }

                                        HttpSession session = request.getSession();
                                        session.setAttribute("currentUser", user);
                                        String role = user.getRole() ? "ROLE_ADMIN" : "ROLE_USER";
                                        List<SimpleGrantedAuthority> authorities = List
                                                        .of(new SimpleGrantedAuthority(role));
                                        Authentication newAuth = new UsernamePasswordAuthenticationToken(user, null,
                                                        authorities);
                                        SecurityContextHolder.getContext().setAuthentication(newAuth);
                                        response.sendRedirect("/home");
                                }));
                // 4. Cấu hình đăng nhập thường (Form Login)
                http.formLogin(form -> form
                                .loginPage("/login/form") // Trang hiển thị form login
                                .loginProcessingUrl("/login") // URL action trong form HTML (<form action="/login">)
                                .defaultSuccessUrl("/login/success", false)
                                .failureUrl("/login/failure")
                                .permitAll());

                // 5. ✅ Cấu hình Remember Me - ĐÃ SỬA
                http.rememberMe(remember -> remember
                                .key("uniqueAndSecret")
                                .tokenValiditySeconds(3 * 24 * 60 * 60) // 3 ngày
                                .rememberMeCookieName("remember-me")
                                .userDetailsService(userService)); // ✅ Phải gọi BÊN TRONG lambda

                // 6. Cấu hình Đăng xuất
                http.logout(logout -> logout
                                .logoutUrl("/logout")
                                .logoutSuccessUrl("/login/form?logout") // Quay về trang login sau khi thoát
                                .clearAuthentication(true)
                                .invalidateHttpSession(true)
                                .deleteCookies("JSESSIONID", "remember-me")
                                .permitAll());
                http.exceptionHandling(exception -> exception
                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                        // Khi bị lỗi 403 (Không có quyền), redirect về trang chủ
                                        response.sendRedirect("/home");
                                }));
                return http.build();
        }
}