package com.example.company_directory.service;

import java.time.LocalDateTime;
import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.company_directory.entity.User;
import com.example.company_directory.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * ユーザー情報を管理するサービス。
 * ユーザー情報の検索を行います。
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * ユーザー名でユーザー情報を取得する。
     * @param username
     * @return ユーザー情報
     * @throws UsernameNotFoundException ユーザーが見つからない場合
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUserId(username)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + username));

        // Check if account is locked
        boolean accountNonLocked = true;
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            accountNonLocked = false;
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUserId(),
                user.getPasswordHash(),
                user.isEnabled(), // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                accountNonLocked, // accountNonLocked
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
