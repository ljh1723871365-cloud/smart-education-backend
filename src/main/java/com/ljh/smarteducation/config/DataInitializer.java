package com.ljh.smarteducation.config;

import com.ljh.smarteducation.entity.User;
import com.ljh.smarteducation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 数据初始化类 - 在应用启动时自动创建默认管理员账户
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已存在管理员账户
        Optional<User> adminOptional = userRepository.findByUsername("admin");
        if (adminOptional.isEmpty()) {
            // 创建默认管理员账户
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("1234")); // 默认密码：1234
            adminUser.setRole("ROLE_ADMIN");
            
            userRepository.save(adminUser);
            System.out.println(">>> 默认管理员账户已创建: username=admin, password=1234");
        } else {
            // 每次启动时重置admin密码，确保密码正确
            User adminUser = adminOptional.get();
            String newPasswordHash = passwordEncoder.encode("1234");
            adminUser.setPassword(newPasswordHash);
            userRepository.save(adminUser);
            System.out.println(">>> 管理员账户已存在，已重置密码: username=admin, password=1234");
            System.out.println(">>> 新密码哈希: " + newPasswordHash);
        }
    }
}

