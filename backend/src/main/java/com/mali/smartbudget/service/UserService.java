package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.ChangePasswordRequest;
import com.mali.smartbudget.dto.UpdateProfileRequest;
import com.mali.smartbudget.dto.UserProfileDto;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileDto getCurrentUserProfile(User user) {
        return toDto(user);
    }

    @Transactional
    public UserProfileDto updateUserProfile(User user, UpdateProfileRequest request) {
        String newEmail = request.email().trim().toLowerCase();

        if (!user.getEmail().equalsIgnoreCase(newEmail)
                && userRepository.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Bu e-posta adresi başka bir hesap tarafından kullanılıyor: " + newEmail);
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(newEmail);
        User saved = userRepository.save(user);
        log.info("Profil güncellendi. userId={}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mevcut şifre hatalı.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Yeni şifre ve şifre tekrarı eşleşmiyor.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Yeni şifre mevcut şifreyle aynı olamaz.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Şifre değiştirildi. userId={}", user.getId());
    }

    @Transactional
    public void saveUser(User user) {
        userRepository.save(user);
    }

    private UserProfileDto toDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getMonthlyBudget(),
                user.getCreatedAt()
        );
    }
}
