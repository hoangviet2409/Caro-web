package com.web_game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String username;
    private String email;
    private int score;
    private int wins;
    private int losses;
    private double winRate;
    private int totalMatches;
    private List<MatchDTO> recentMatches;
}

