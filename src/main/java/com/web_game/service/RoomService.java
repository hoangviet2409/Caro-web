package com.web_game.service;

import com.web_game.dto.RoomDTO;
import com.web_game.service.GameService.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RoomService {

    @Autowired
    private GameService gameService;

    public List<RoomDTO> getAllRooms() {
        List<RoomDTO> rooms = new ArrayList<>();
        Map<String, GameSession> activeGames = gameService.getAllActiveGames();

        for (Map.Entry<String, GameSession> entry : activeGames.entrySet()) {
            String roomId = entry.getKey();
            GameSession session = entry.getValue();
            
            RoomDTO dto = new RoomDTO();
            dto.setRoomId(roomId);
            dto.setPlayerXName(session.playerX_Name != null ? session.playerX_Name : "");
            dto.setPlayerOName(session.playerO_Name != null ? session.playerO_Name : "");
            dto.setFull(session.playerX_Name != null && session.playerO_Name != null);
            dto.setGameOver(session.isGameOver);
            dto.setCurrentPlayer(session.currentPlayer);
            
            // Đếm số nước đi
            int moveCount = 0;
            for (int[] row : session.board) {
                for (int cell : row) {
                    if (cell != 0) moveCount++;
                }
            }
            dto.setMoveCount(moveCount);
            
            rooms.add(dto);
        }
        return rooms;
    }

    public RoomDTO getRoomInfo(String roomId) {
        List<RoomDTO> allRooms = getAllRooms();
        return allRooms.stream()
                .filter(r -> r.getRoomId().equals(roomId))
                .findFirst()
                .orElse(null);
    }
}

