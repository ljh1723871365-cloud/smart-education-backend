package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.config.JwtUtils;
import com.ljh.smarteducation.dto.JwtResponse;
import com.ljh.smarteducation.dto.LoginRequest;
import com.ljh.smarteducation.entity.User;
import com.ljh.smarteducation.repository.UserRepository;
import com.ljh.smarteducation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * POST /api/auth/register - 学生注册接口
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody LoginRequest registerRequest) {
        // 检查用户名是否已存在
        if (userService.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Error: Username is already taken!");
        }

        // 创建新用户
        User newUser = new User();
        newUser.setUsername(registerRequest.getUsername());
        // 注意：密码在userService.createUser中会被加密
        newUser.setPassword(registerRequest.getPassword());
        newUser.setRole("ROLE_STUDENT"); // 注册的用户默认为学生
        userService.createUser(newUser);

        return ResponseEntity.ok("User registered successfully!");
    }

    /**
     * POST /api/auth/login - 学生登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody(required = false) LoginRequest loginRequest) {
        System.out.println(">>> ========== 登录请求开始 ==========");
        System.out.println(">>> LoginRequest对象: " + (loginRequest != null ? "存在" : "null"));
        if (loginRequest == null) {
            System.err.println(">>> 错误: LoginRequest 为 null，请求体可能无法解析");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error: Request body is required.");
        }
        System.out.println(">>> 登录请求: username=" + loginRequest.getUsername());
        System.out.println(">>> 登录请求: password=" + (loginRequest.getPassword() != null ? "***" : "null"));
        
        // 1. 从数据库查找用户
        Optional<User> userOptional = userService.findByUsername(loginRequest.getUsername());

        if (userOptional.isEmpty()) {
            System.err.println(">>> 登录失败: 用户不存在 username=" + loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Invalid username or password.");
        }

        User user = userOptional.get();
        System.out.println(">>> 找到用户: username=" + user.getUsername() + ", role=" + user.getRole());
        System.out.println(">>> 数据库密码哈希: " + user.getPassword());
        System.out.println(">>> 输入的密码: " + loginRequest.getPassword());

        // 2. 验证密码
        boolean passwordMatches = passwordEncoder.matches(loginRequest.getPassword(), user.getPassword());
        System.out.println(">>> 密码匹配结果: " + passwordMatches);

        if (!passwordMatches) {
            System.err.println(">>> 登录失败: 密码不匹配");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Invalid username or password.");
        }

        // 3. 如果密码正确，生成JWT
        String jwt = jwtUtils.generateJwtToken(user);
        System.out.println(">>> 登录成功，生成JWT: " + jwt.substring(0, Math.min(20, jwt.length())) + "...");

        // 4. 返回JWT和用户信息
        return ResponseEntity.ok(new JwtResponse(jwt, user.getId(), user.getUsername(), user.getRole()));
    }

    /**
     * GET /api/auth/debug/password - 调试端点，验证密码匹配
     */
    @GetMapping("/debug/password")
    public ResponseEntity<?> debugPassword() {
        Optional<User> adminOptional = userService.findByUsername("admin");
        if (adminOptional.isEmpty()) {
            return ResponseEntity.ok("Admin user not found");
        }
        
        User admin = adminOptional.get();
        String storedHash = admin.getPassword();
        String testPassword = "1234";
        boolean matches = passwordEncoder.matches(testPassword, storedHash);
        
        return ResponseEntity.ok(String.format(
            "Admin user found: %s\nStored hash: %s\nTest password: %s\nMatches: %s",
            admin.getUsername(), storedHash, testPassword, matches
        ));
    }

    /**
     * One-time runner to reset student1's password on startup.
     * This is a temporary measure to ensure we can log in for testing.
     */
    // @Bean  // (Disabled)
    // public CommandLineRunner resetStudentPassword(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    //     return args -> {
    //         Optional<User> studentOptional = userRepository.findByUsername("student1");
    //         if (studentOptional.isPresent()) {
    //             User student = studentOptional.get();
    //             String newPassword = "password123";
    //             String encodedPassword = passwordEncoder.encode(newPassword);
    //             
    //             // Only update if the password has changed
    //             if (!passwordEncoder.matches(newPassword, student.getPassword())) {
    //                 student.setPassword(encodedPassword);
    //                 userRepository.save(student);
    //                 System.out.println(">>> [PasswordResetRunner] Successfully reset password for 'student1'.");
    //             } else {
    //                 System.out.println(">>> [PasswordResetRunner] Password for 'student1' is already set. No action needed.");
    //             }
    //         } else {
    //             System.out.println(">>> [PasswordResetRunner] User 'student1' not found. Cannot reset password.");
    //         }
    //     };
    // }
}