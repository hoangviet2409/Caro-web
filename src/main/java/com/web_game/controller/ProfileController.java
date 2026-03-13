package com.web_game.controller;

import com.web_game.dto.MatchDTO;
import com.web_game.dto.UserProfileDTO;
import com.web_game.model.MatchHistory;
import com.web_game.model.User;
import com.web_game.repository.MatchRepository;
import com.web_game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRepository matchRepository;

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserProfileDTO profile = new UserProfileDTO();
        profile.setUsername(user.getUsername());
        profile.setEmail(user.getEmail());
        profile.setScore(user.getScore());
        profile.setWins(user.getWins());
        profile.setLosses(user.getLosses());
        profile.setTotalMatches(user.getWins() + user.getLosses());
        
        if (profile.getTotalMatches() > 0) {
            profile.setWinRate((double) user.getWins() / profile.getTotalMatches() * 100);
        } else {
            profile.setWinRate(0.0);
        }

        // Lấy 10 trận đấu gần nhất
        List<MatchHistory> matches = matchRepository.findByWinnerUsernameOrLoserUsernameOrderByPlayedAtDesc(
                username, username, PageRequest.of(0, 10));
        
        List<MatchDTO> matchDTOs = matches.stream().map(m -> {
            MatchDTO dto = new MatchDTO();
            dto.setId(m.getId());
            dto.setRoomId(m.getRoomId());
            dto.setWinnerUsername(m.getWinnerUsername());
            dto.setWinnerRole(m.getWinnerRole());
            dto.setLoserUsername(m.getLoserUsername());
            dto.setPlayedAt(m.getPlayedAt());
            return dto;
        }).collect(Collectors.toList());

        profile.setRecentMatches(matchDTOs);
        return ResponseEntity.ok(profile);
    }
}

