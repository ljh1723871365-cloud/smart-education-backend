package com.ljh.smarteducation.config; // <-- 已更新为您的包名

import com.ljh.smarteducation.service.impl.UserDetailsServiceImpl; // <-- 已更新为您的包名
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 这个类不需要 @Component 注解，因为它是在 SecurityConfig 中通过 @Bean 创建的
public class AuthTokenFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    // 使用构造函数注入依赖
    public AuthTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
                try {
                    // 1. 从请求头中解析JWT
                    String jwt = parseJwt(request);

                    if (jwt != null) {
                        // 2. 验证JWT是否有效
                        if (jwtUtils.validateJwtToken(jwt)) {
                            // 3. 如果有效，从中获取用户名
                            String username = jwtUtils.getUserNameFromJwtToken(jwt);

                            // 4. 从数据库加载用户信息
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                            // 5. 创建一个认证对象
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            // 6. 将认证信息设置到Spring Security的上下文中，表示当前用户已通过认证
                            SecurityContextHolder.getContext().setAuthentication(authentication);

                            System.out.println(">>> [AuthTokenFilter] JWT验证成功，用户: " + username + ", 路径: " + request.getRequestURI());
                            System.out.println(">>> [AuthTokenFilter] 用户权限: " + userDetails.getAuthorities());
                            System.out.println(">>> [AuthTokenFilter] SecurityContext认证状态: " + (SecurityContextHolder.getContext().getAuthentication() != null ? "已设置" : "未设置"));
                            System.out.println(">>> [AuthTokenFilter] 当前认证对象: " + SecurityContextHolder.getContext().getAuthentication());
                            logger.debug("JWT验证成功，用户: {}, 路径: {}", username, request.getRequestURI());
                        } else {
                            System.err.println(">>> [AuthTokenFilter] JWT验证失败，路径: " + request.getRequestURI());
                            logger.warn("JWT验证失败，路径: {}", request.getRequestURI());
                        }
                    } else {
                        // 对于需要认证的API，如果没有token会返回401
                        if (request.getRequestURI().startsWith("/api/admin/")) {
                            System.err.println(">>> [AuthTokenFilter] 未找到JWT token，路径: " + request.getRequestURI());
                            System.err.println(">>> [AuthTokenFilter] Authorization header: " + request.getHeader("Authorization"));
                            logger.debug("未找到JWT token，路径: {}", request.getRequestURI());
                        }
                    }
                    
                    // 特别针对 /api/admin/questions 的调试日志
                    if (request.getRequestURI().equals("/api/admin/questions")) {
                        System.out.println(">>> [AuthTokenFilter] ========== /api/admin/questions 请求调试 ==========");
                        System.out.println(">>> [AuthTokenFilter] JWT token: " + (jwt != null ? jwt.substring(0, Math.min(30, jwt.length())) + "..." : "null"));
                        System.out.println(">>> [AuthTokenFilter] SecurityContext认证状态: " + (SecurityContextHolder.getContext().getAuthentication() != null ? "已设置" : "未设置"));
                        if (SecurityContextHolder.getContext().getAuthentication() != null) {
                            System.out.println(">>> [AuthTokenFilter] 当前认证对象: " + SecurityContextHolder.getContext().getAuthentication());
                            System.out.println(">>> [AuthTokenFilter] 用户权限: " + SecurityContextHolder.getContext().getAuthentication().getAuthorities());
                        }
                        System.out.println(">>> [AuthTokenFilter] ================================================");
                    }
                } catch (Exception e) {
                    System.err.println(">>> [AuthTokenFilter] ========== EXCEPTION CAUGHT ==========");
                    System.err.println(">>> [AuthTokenFilter] URI: " + request.getRequestURI());
                    System.err.println(">>> [AuthTokenFilter] Error: " + e.getMessage());
                    e.printStackTrace(System.err);
                    System.err.println(">>> [AuthTokenFilter] =========================================");
                    logger.error("Cannot set user authentication: {}", e.getMessage(), e);
                }

        // 7. 放行请求，让它继续访问后续的Controller
        filterChain.doFilter(request, response);
    }

    /**
     * 从 "Authorization" 请求头中解析出 "Bearer <token>"
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}