package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp; // 1. (新增) 导入时间戳

import java.time.LocalDateTime; // 2. (新增) 导入时间库

@Entity
@Data
@Table(name = "`users`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // e.g., "ROLE_STUDENT", "ROLE_ADMIN", "ROLE_TEACHER"

    // --- ↓↓↓ (新增) 字段用于统计 ↓↓↓ ---
    @CreationTimestamp
    @Column(updatable = false) // 允许旧数据为 null，但新数据会自动填充
    private LocalDateTime createTime; // 用户的注册时间
    // --- ↑↑↑ (新增) 字段结束 ↑↑↑ ---
}

