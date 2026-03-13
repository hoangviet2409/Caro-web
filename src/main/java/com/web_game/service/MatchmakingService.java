package com.web_game.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Matchmaking đơn giản: giữ một người đang chờ, người tiếp theo vào sẽ ghép với cùng roomId.
 */
@Service
public class MatchmakingService {

    private String waitingUsername;
    private String waitingRoomId;

    public static class MatchResult {
        public String roomId;
        public String role;   // "X" hoặc "O"
        public String status; // "WAIT" hoặc "MATCHED"
    }

    public synchronized MatchResult quickMatch(String username) {
        MatchResult result = new MatchResult();

        if (waitingUsername == null || waitingUsername.equals(username)) {
            // Không ai đang chờ -> tạo room mới và chờ đối thủ
            waitingUsername = username;
            waitingRoomId = "quick-" + UUID.randomUUID().toString().substring(0, 8);

            result.roomId = waitingRoomId;
            result.role = "X";
            result.status = "WAIT";
        } else {
            // Ghép với người đang chờ
            result.roomId = waitingRoomId;
            result.role = "O";
            result.status = "MATCHED";

            // Reset hàng chờ
            waitingUsername = null;
            waitingRoomId = null;
        }
        return result;
    }
}


