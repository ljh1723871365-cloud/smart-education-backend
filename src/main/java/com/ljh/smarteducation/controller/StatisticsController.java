package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.repository.StudentSubmissionRepository;
import com.ljh.smarteducation.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/statistics")
@CrossOrigin(origins = "http://localhost:5173") // 仅允许后台管理端
@PreAuthorize("hasRole('ADMIN')") // 确保只有管理员能访问
public class StatisticsController {

    private final UserRepository userRepository;
    private final StudentSubmissionRepository submissionRepository;

    public StatisticsController(UserRepository userRepository, StudentSubmissionRepository submissionRepository) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * GET /api/admin/statistics/dashboard - 获取仪表盘核心数据
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 1. 按角色统计用户总数
        long totalStudents = userRepository.countByRole("ROLE_STUDENT");
        long totalTeachers = userRepository.countByRole("ROLE_TEACHER"); // (新增)
        long totalAdmins = userRepository.countByRole("ROLE_ADMIN");

        // 2. 统计答卷总数
        long totalSubmissions = submissionRepository.count();
        long pendingSubmissions = submissionRepository.countByGraded(false); // 待批改

        // 3. (新增) 统计今日新注册用户
        LocalDate today = LocalDate.now();
        long newUsersToday = userRepository.countByCreateTimeGreaterThanEqual(today.atStartOfDay(ZoneOffset.UTC).toLocalDateTime());

        stats.put("totalStudents", totalStudents);
        stats.put("totalTeachers", totalTeachers);
        stats.put("totalAdmins", totalAdmins);
        stats.put("totalSubmissions", totalSubmissions);
        stats.put("pendingSubmissions", pendingSubmissions);
        stats.put("newUsersToday", newUsersToday);

        return ResponseEntity.ok(stats);
    }
}

