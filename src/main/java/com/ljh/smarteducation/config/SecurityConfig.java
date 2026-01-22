package com.ljh.smarteducation.config; // 确认是您的包名

import com.ljh.smarteducation.service.impl.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // 1. (推荐) 开启方法级安全
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.core.Authentication;

@Configuration
@EnableMethodSecurity(prePostEnabled = true) // 明确启用方法级安全，允许 @PreAuthorize 生效
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        return new AuthTokenFilter(jwtUtils, userDetailsService);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // (原有的) 定义全局 CORS 配置 Bean
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许后台和学生端
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:5174"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) throws Exception {
        http
                .cors(withDefaults()) // 启用全局 CORS
                .csrf(csrf -> csrf.disable()) // 禁用 CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态 Session
                .authorizeHttpRequests(auth -> auth
                        // 1. 允许公开访问的路径
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/files/**").permitAll()
                        .requestMatchers("/api/admin/generate-word").permitAll() // Word文档生成接口允许公开访问
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()

                        // 2. 允许未认证访问获取套题列表和套题详情（公开信息）
                        .requestMatchers(HttpMethod.GET, "/api/practice/sets").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/practice/set/**").permitAll()
                        
                        // 3. 提交答案等操作需要认证
                        .requestMatchers("/api/practice/**").authenticated()
                        
                        // 4. 其他所有请求都需要认证 (主要针对 admin)
                        .anyRequest().authenticated()
                );

            // 在处理用户名密码认证之前，添加 JWT 过滤器
            http.addFilterBefore(authenticationJwtTokenFilter(jwtUtils, userDetailsService), UsernamePasswordAuthenticationFilter.class);

            // --- ↓↓↓ (核心修改) 更新调试日志，专门监控学生端API ---
            http.addFilterBefore((request, response, chain) -> {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String uri = httpRequest.getRequestURI();
                if (uri.startsWith("/api/practice/")) { // 监控所有练习API
                    System.out.println(">>> [DebugFilter] 拦截到请求: " + httpRequest.getMethod() + " " + uri);
                    System.out.println(">>> [DebugFilter] Authorization Header: " + httpRequest.getHeader("Authorization"));
                    Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null) {
                        System.out.println(">>> [DebugFilter] SecurityContext中的认证对象: " + authentication);
                        System.out.println(">>> [DebugFilter] 用户名: " + authentication.getName());
                        System.out.println(">>> [DebugFilter] 用户权限: " + authentication.getAuthorities());
                    } else {
                        System.out.println(">>> [DebugFilter] SecurityContext中没有认证对象。");
                    }
                }
                chain.doFilter(request, response);
            }, AuthTokenFilter.class); // 放在AuthTokenFilter之后，确保认证信息已被设置
            // --- ↑↑↑ 修改结束 ↑↑↑ ---

            // 添加异常处理过滤器（用于调试 AccessDeniedException）
            http.exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String uri = request.getRequestURI();
                    if (uri.startsWith("/api/admin/questions") || uri.startsWith("/api/practice/")) {
                        System.err.println(">>> [SecurityFilter] AccessDeniedException 被抛出: " + uri);
                        System.err.println(">>> [SecurityFilter] 异常消息: " + accessDeniedException.getMessage());
                        System.err.println(">>> [SecurityFilter] 当前认证: " + (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() : "null"));
                        accessDeniedException.printStackTrace();
                    }
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Access denied: " + accessDeniedException.getMessage() + "\"}");
                })
            );

        return http.build();
    }
}