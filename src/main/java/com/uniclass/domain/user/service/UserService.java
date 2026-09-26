package com.uniclass.domain.user.service;

import com.uniclass.domain.user.dto.UserRegisterDto;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.security.CustomUserDetails;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long register(UserRegisterDto dto) {
        if (!dto.getPassword().equals(dto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByStudentNo(dto.getStudentNo())) {
            throw new IllegalArgumentException("이미 등록된 학번/교번입니다.");
        }

        User user = User.builder()
                .email(dto.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName().trim())
                .studentNo(dto.getStudentNo().trim())
                .role(dto.getRole())
                .build();

        try {
            return userRepository.save(user).getId();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 가입된 이메일 또는 학번/교번입니다.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("가입되지 않은 이메일입니다."));

        return new CustomUserDetails(user);
    }
}
