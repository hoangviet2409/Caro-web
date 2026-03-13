package com.web_game.controller;

import com.web_game.config.JwtUtils;
import com.web_game.model.User;
import com.web_game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired AuthenticationManager authenticationManager;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!");
            }
            if (user.isLocked()) {
                return ResponseEntity.badRequest().body("Tài khoản đã bị khoá tạm thời do đăng nhập sai quá nhiều lần.");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Đăng nhập thành công -> reset failedLoginAttempts
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            String jwt = jwtUtils.generateJwtToken(username);
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("username", username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Đăng nhập thất bại -> tăng failedLoginAttempts
            String username = loginRequest.get("username");
            userRepository.findByUsername(username).ifPresent(u -> {
                int attempts = u.getFailedLoginAttempts() + 1;
                u.setFailedLoginAttempts(attempts);
                if (attempts >= 5) {
                    u.setLocked(true);
                }
                userRepository.save(u);
            });
            return ResponseEntity.badRequest().body("Đăng nhập thất bại: Sai tài khoản hoặc mật khẩu!");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.get("username"))) {
            return ResponseEntity.badRequest().body("Lỗi: Username đã tồn tại!");
        }
        if (userRepository.existsByEmail(signUpRequest.get("email"))) {
            return ResponseEntity.badRequest().body("Lỗi: Email đã được sử dụng!");
        }
        User user = new User(signUpRequest.get("username"), signUpRequest.get("email"), passwordEncoder.encode(signUpRequest.get("password")));
        userRepository.save(user);
        return ResponseEntity.ok("Đăng ký thành công!");
    }

    // Đổi mật khẩu (đã đăng nhập)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("Không tìm thấy người dùng.");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Mật khẩu cũ không đúng.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLocked(false);
        userRepository.save(user);
        return ResponseEntity.ok("Đổi mật khẩu thành công.");
    }
}