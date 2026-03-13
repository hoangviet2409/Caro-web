package com.web_game.controller;

import com.web_game.model.GameState;
import com.web_game.service.GameService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

    @Autowired
    private GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 1. JOIN ROOM
    @MessageMapping("/join/{roomId}")
    public void handleJoinRoom(@DestinationVariable String roomId, @Payload String username,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        // Ưu tiên lấy username từ session (được set bởi WebSocketConfig từ JWT)
        Object usernameAttr = headerAccessor.getSessionAttributes() != null
                ? headerAccessor.getSessionAttributes().get("username")
                : null;
        String effectiveUsername = usernameAttr instanceof String ? (String) usernameAttr : username;

        String role = gameService.joinRoom(roomId, sessionId, effectiveUsername);
        GameState currentState = gameService.getGameState(roomId);
        messagingTemplate.convertAndSend("/topic/specific-user/" + sessionId, new JoinResponse(role, currentState));
    }

    // ...

    // --- DTO Classes ---
    @Data
    @AllArgsConstructor
    static class JoinResponse {
        private String role;
        private GameState currentState;
    }

    // 2. MOVE
    @MessageMapping("/move/{roomId}")
    public void handleMove(@DestinationVariable String roomId, @Payload MoveMessage move,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        GameState state = gameService.makeMove(roomId, sessionId, move.getRow(), move.getCol());
        if (state != null) {
            messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, state);
        }
    }

    // 3. CHAT
    @MessageMapping("/chat/{roomId}")
    public void handleChat(@DestinationVariable String roomId, @Payload ChatMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        message.setSenderId(sessionId);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
    }

    // 4. NEW GAME
    @MessageMapping("/new-game/{roomId}")
    public void handleNewGame(@DestinationVariable String roomId) {
        gameService.newGame(roomId);
        GameState resetState = new GameState();
        resetState.setPlayer("RESET");
        messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, resetState);
    }

    // 5. SURRENDER (Đầu hàng)
    @MessageMapping("/surrender/{roomId}")
    public void handleSurrender(@DestinationVariable String roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        GameState state = gameService.surrender(roomId, sessionId);
        if (state != null) {
            messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, state);
        }
    }

    // 6. REQUEST REMATCH
    @MessageMapping("/rematch/{roomId}")
    public void handleRematch(@DestinationVariable String roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = (String) (headerAccessor.getSessionAttributes() != null
                ? headerAccessor.getSessionAttributes().get("username")
                : null);
        if (username == null)
            username = "Guest";

        boolean ready = gameService.requestRematch(roomId, sessionId, username);
        messagingTemplate.convertAndSend("/topic/rematch/" + roomId, new RematchResponse(ready, username));
    }

    // 7. KICK SPECTATOR
    @MessageMapping("/kick/{roomId}")
    public void handleKickSpectator(@DestinationVariable String roomId, @Payload KickMessage kickMsg,
            SimpMessageHeaderAccessor headerAccessor) {
        String ownerSessionId = headerAccessor.getSessionId();
        boolean success = gameService.kickSpectator(roomId, ownerSessionId, kickMsg.getSpectatorSessionId());
        if (success) {
            // Gửi thông báo cho spectator bị kick
            messagingTemplate.convertAndSend("/topic/kicked/" + kickMsg.getSpectatorSessionId(),
                    new KickNotification(roomId));
        }
    }

    // 8. DRAW REQUEST
    @MessageMapping("/draw-request/{roomId}")
    public void handleDrawRequest(@DestinationVariable String roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        System.out.println("Draw request received for room: " + roomId + " from session: " + sessionId);
        gameService.processDrawRequest(roomId, sessionId);
    }

    // 9. DRAW RESPONSE
    @MessageMapping("/draw-response/{roomId}")
    public void handleDrawResponse(@DestinationVariable String roomId, @Payload DrawResponseMessage response,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        gameService.processDrawResponse(roomId, sessionId, response.isAccepted());
    }

    // --- DTO Classes ---

    @Data
    static class MoveMessage {
        private int row;
        private int col;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ChatMessage {
        private String content;
        private String senderId;
        private String senderName;
    }

    @Data
    @AllArgsConstructor
    static class RematchResponse {
        private boolean ready;
        private String username;
    }

    @Data
    static class KickMessage {
        private String spectatorSessionId;
    }

    @Data
    @AllArgsConstructor
    static class KickNotification {
        private String roomId;
    }

    @Data
    static class DrawResponseMessage {
        private boolean accepted;
    }
}