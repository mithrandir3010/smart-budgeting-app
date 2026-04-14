package com.mali.smartbudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder bean'ini ayrı bir sınıfta tanımlayarak SecurityConfig'deki
 * dairesel bağımlılığı (circular dependency) önler.
 *
 * <pre>
 * SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder
 *       ↑_______________________________________________|   (cycle!)
 * </pre>
 *
 * Bu sınıf cycle dışında tutularak AuthService'in PasswordEncoder'a
 * SecurityConfig'den bağımsız erişmesini sağlar.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
