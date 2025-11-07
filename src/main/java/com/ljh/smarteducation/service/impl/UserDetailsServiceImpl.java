package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.entity.User;
import com.ljh.smarteducation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        // --- ↓↓↓ (核心修复) 确保所有角色都被正确授予 "ROLE_" 前缀 ---
        String role = user.getRole();
        if (role == null) {
            // 如果用户没有角色，给予一个默认的、无权限的角色
            return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                    Collections.emptyList());
        }
        
        // 如果角色字符串没有 "ROLE_" 前缀，则添加它
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(role)));
        // --- ↑↑↑ 修复结束 ↑↑↑ ---
    }
}