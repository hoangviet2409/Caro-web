package com.web_game.config;

import com.web_game.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    JwtAuthenticationFilter jwtAuthenticationFilter;

    // 1. Bean mã hóa mật khẩu (Giữ nguyên)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. AuthenticationManager (Giữ nguyên - Spring sẽ tự tìm
    // UserDetailsServiceImpl và PasswordEncoder để cấu hình cho cái này)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // --- ĐÃ XÓA BEAN DaoAuthenticationProvider GÂY LỖI ---
    // (Spring Boot sẽ tự động cấu hình nó ngầm bên dưới)

    // 3. SecurityFilterChain (Cập nhật, thêm JWT filter)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/ws-caro/**").permitAll()
                        .requestMatchers("/", "/index.html", "/login.html", "/lobby.html", "/profile.html",
                                "/home.html", "/match-history.html",
                                "/*.css", "/*.js", "/favicon.ico", "/**/*.html", "/**/*.png", "/**/*.jpg", "/**/*.jpeg",
                                "/**/*.gif", "/**/*.svg", "/**/*.woff", "/**/*.woff2", "/**/*.ttf", "/**/*.map")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}