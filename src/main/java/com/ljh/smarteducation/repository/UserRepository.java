package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA will automatically implement this method for us.
    // It's useful for checking if a username already exists.
    Optional<User> findByUsername(String username);

    // --- ↓↓↓ (新增) 用于统计的方法 ↓↓↓ ---

    /**
     * 2. (新增) 按角色统计用户数量
     */
    long countByRole(String role);

    /**
     * 3. (新增) 统计某个时间点后注册的用户数量
     */
    long countByCreateTimeGreaterThanEqual(LocalDateTime dateTime);

    // --- ↑↑↑ (新增) 方法结束 ↑↑↑ ---


}