package com.web_game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDTO {
    private String roomId;
    private String playerXName;
    private String playerOName;
    private boolean isFull;
    private boolean isGameOver;
    private String currentPlayer;
    private int moveCount;
}

