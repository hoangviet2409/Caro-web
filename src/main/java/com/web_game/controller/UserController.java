package com.web_game.controller;

import com.web_game.model.User;
import com.web_game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    UserRepository userRepository;

    // API lấy Top 10 cao thủ: http://localhost:8080/api/users/leaderboard
    @GetMapping("/leaderboard")
    public List<User> getLeaderboard() {
        return userRepository.findTop10ByOrderByScoreDesc();
    }
}