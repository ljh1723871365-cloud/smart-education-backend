package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserService {

    User createUser(User user);

    List<User> getAllUsers();

    Optional<User> findByUsername(String username);

    // 未来在这里添加 updateUser 和 deleteUser 等方法的签名
}