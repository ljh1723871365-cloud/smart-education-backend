package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.User;
import com.ljh.smarteducation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // 1. (推荐) 确保这个控制器受保护
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set; // 2. (新增) 导入 Set

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "http://localhost:5173")
@PreAuthorize("hasRole('ADMIN')") // 3. (推荐) 确保整个控制器只有ADMIN能访问
public class UserController {

    private final UserService userService;

    // --- (新增) 允许的角色列表 ---
    private static final Set<String> VALID_ROLES = Set.of("ROLE_STUDENT", "ROLE_ADMIN", "ROLE_TEACHER");

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/admin/users - 创建一个新用户
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody User user) {
        // 检查用户名是否已存在
        if (userService.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT) // 409 Conflict
                    .body("Error: Username is already taken!");
        }

        // --- ↓↓↓ (核心修改) 检查角色是否有效 ↓↓↓ ---
        if (user.getRole() == null || !VALID_ROLES.contains(user.getRole())) {
            return ResponseEntity
                    .badRequest() // 400 Bad Request
                    .body("Error: Role is not valid. Must be ROLE_STUDENT, ROLE_TEACHER, or ROLE_ADMIN.");
        }
        // --- ↑↑↑ (修改结束) ↑↑↑ ---

        User createdUser = userService.createUser(user);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED); // 201 Created
    }

    /**
     * GET /api/admin/users - 获取所有用户
     */
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // 未来可以添加 getUserById, updateUser, deleteUser 等API端点
}
