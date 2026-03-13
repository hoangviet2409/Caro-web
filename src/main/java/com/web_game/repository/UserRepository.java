package com.web_game.repository;

import com.web_game.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    // Tìm user bằng username
    Optional<User> findByUsername(String username);

    // Kiểm tra tồn tại
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    // --- LẤY TOP 10 NGƯỜI ĐIỂM CAO NHẤT ---
    List<User> findTop10ByOrderByScoreDesc();
}