package com.web_game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password; // Mật khẩu đã mã hóa

    private String email;

    // --- CÁC TRƯỜNG MỚI CHO BẢNG XẾP HẠNG ---
    private int score = 1000; // Điểm mặc định
    private int wins = 0;
    private int losses = 0;

    // --- BẢO MẬT & QUẢN TRỊ ---
    private int failedLoginAttempts = 0;   // Số lần đăng nhập sai liên tiếp
    private boolean locked = false;        // Tài khoản bị khoá tạm thời
    private boolean admin = false;         // Quyền admin (tạo tay trong DB hoặc future UI)

    // Constructor rỗng (Bắt buộc)
    public User() {
    }

    // Constructor dùng lúc đăng ký
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // --- GETTERS VÀ SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}