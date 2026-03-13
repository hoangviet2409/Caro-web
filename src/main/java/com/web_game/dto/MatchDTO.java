package com.web_game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchDTO {
    private String id;
    private String roomId;
    private String winnerUsername;
    private String winnerRole;
    private String loserUsername;
    private LocalDateTime playedAt;
}

