package com.web_game.config;

import com.web_game.model.GameState;
import com.web_game.service.GameService;
import com.web_game.service.GameService.DisconnectResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Lắng nghe sự kiện WebSocket disconnect để xử lý khi người chơi out phòng.
 */
@Component
public class WebSocketEventListener {

    @Autowired
    private GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        DisconnectResult result = gameService.handleDisconnect(sessionId);
        if (result != null && result.getState() != null) {
            GameState state = result.getState();
            messagingTemplate.convertAndSend("/topic/game-progress/" + result.getRoomId(), state);
        }
    }
}


