package com.web_game.controller;

import com.web_game.service.MatchmakingService;
import com.web_game.service.MatchmakingService.MatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/match")
public class MatchmakingController {

    @Autowired
    private MatchmakingService matchmakingService;

    @GetMapping("/quick")
    public ResponseEntity<?> quickMatch() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        if (username == null || "anonymousUser".equals(username)) {
            return ResponseEntity.status(401).body("Bạn cần đăng nhập.");
        }

        MatchResult result = matchmakingService.quickMatch(username);
        Map<String, Object> resp = new HashMap<>();
        resp.put("roomId", result.roomId);
        resp.put("role", result.role);
        resp.put("status", result.status);
        return ResponseEntity.ok(resp);
    }
}


