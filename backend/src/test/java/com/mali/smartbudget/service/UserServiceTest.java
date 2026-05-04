package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.ChangePasswordRequest;
import com.mali.smartbudget.dto.UpdateProfileRequest;
import com.mali.smartbudget.dto.UserProfileDto;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Birim Testleri")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    private User user;
    private static final String CURRENT_PASSWORD_HASH = "$2a$10$mockedHashForCurrentPassword";

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password(CURRENT_PASSWORD_HASH)
                .fullName("Test Kullanıcı")
                .build();
    }

    // =========================================================================
    // updateUserProfile
    // =========================================================================

    @Nested
    @DisplayName("updateUserProfile")
    class UpdateUserProfileTests {

        @Test
        @DisplayName("Başarılı güncelleme: ad ve e-posta güncellenir, DTO döner")
        void updateUserProfile_success_returnsUpdatedDto() {
            UpdateProfileRequest request = new UpdateProfileRequest("Yeni Ad Soyad", "yeni@ornek.com");
            when(userRepository.existsByEmail("yeni@ornek.com")).thenReturn(false);
            when(userRepository.save(user)).thenReturn(user);

            UserProfileDto result = userService.updateUserProfile(user, request);

            assertThat(user.getFullName()).isEqualTo("Yeni Ad Soyad");
            assertThat(user.getEmail()).isEqualTo("yeni@ornek.com");
            assertThat(result).isNotNull();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("E-posta değişmiyorsa (büyük/küçük harf fark gözetmeksizin) duplicate kontrolü atlanır")
        void updateUserProfile_sameEmail_skipsExistsByEmailCheck() {
            UpdateProfileRequest request = new UpdateProfileRequest("Güncel Ad", "TEST@EXAMPLE.COM");
            when(userRepository.save(user)).thenReturn(user);

            userService.updateUserProfile(user, request);

            verify(userRepository, never()).existsByEmail(any());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("E-posta çakışması: başka hesap aynı e-postayı kullanıyorsa hata fırlatılır")
        void updateUserProfile_duplicateEmail_throwsIllegalArgumentException() {
            UpdateProfileRequest request = new UpdateProfileRequest("Yeni Ad", "cakisan@ornek.com");
            when(userRepository.existsByEmail("cakisan@ornek.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateUserProfile(user, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cakisan@ornek.com");

            verify(userRepository, never()).save(any());
        }
    }

    // =========================================================================
    // changePassword
    // =========================================================================

    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTests {

        @Test
        @DisplayName("Başarılı şifre değişimi: yeni şifre encode edilerek kaydedilir")
        void changePassword_success_savesEncodedNewPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "mevcutSifre", "yeniSifre123", "yeniSifre123"
            );
            when(passwordEncoder.matches("mevcutSifre", CURRENT_PASSWORD_HASH)).thenReturn(true);
            when(passwordEncoder.matches("yeniSifre123", CURRENT_PASSWORD_HASH)).thenReturn(false);
            when(passwordEncoder.encode("yeniSifre123")).thenReturn("$2a$10$yeniHashlenmisSifre");

            userService.changePassword(user, request);

            assertThat(user.getPassword()).isEqualTo("$2a$10$yeniHashlenmisSifre");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Yanlış mevcut şifre: IllegalArgumentException fırlatılır, kayıt yapılmaz")
        void changePassword_wrongCurrentPassword_throwsIllegalArgumentException() {
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "yanlisSifre", "yeniSifre123", "yeniSifre123"
            );
            when(passwordEncoder.matches("yanlisSifre", CURRENT_PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(user, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mevcut şifre hatalı");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Şifre onayı eşleşmiyor: IllegalArgumentException fırlatılır, kayıt yapılmaz")
        void changePassword_confirmMismatch_throwsIllegalArgumentException() {
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "mevcutSifre", "yeniSifre123", "farkliSifre456"
            );
            when(passwordEncoder.matches("mevcutSifre", CURRENT_PASSWORD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(user, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eşleşmiyor");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Yeni şifre mevcut şifreyle aynı: IllegalArgumentException fırlatılır, kayıt yapılmaz")
        void changePassword_newPasswordSameAsCurrent_throwsIllegalArgumentException() {
            ChangePasswordRequest request = new ChangePasswordRequest(
                    "mevcutSifre", "mevcutSifre", "mevcutSifre"
            );
            when(passwordEncoder.matches("mevcutSifre", CURRENT_PASSWORD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(user, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aynı olamaz");

            verify(userRepository, never()).save(any());
        }
    }
}
