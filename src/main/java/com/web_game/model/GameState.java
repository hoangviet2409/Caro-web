package com.web_game.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameState {
    private Integer row;
    private Integer col;
    private String player; // "X" hoặc "O"
    private String nextTurn; // Lượt tiếp theo
    private String winner; // Người thắng (nếu có)
    private java.util.List<int[]> winningLine; // Đường thắng (danh sách [row, col])
    private Integer moveNumber; // Số thứ tự nước đi
    private String endReason; // Lý do kết thúc (VD: OPPONENT_LEFT, NORMAL, TIME_OUT, AFK)
    private Integer timeRemaining; // Thời gian còn lại (giây) cho lượt hiện tại
    private java.util.List<int[]> moveHistory; // Lịch sử nước đi (để sync)
}