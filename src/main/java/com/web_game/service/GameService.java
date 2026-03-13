package com.web_game.service;

import com.web_game.model.GameState;
import com.web_game.model.MatchHistory;
import com.web_game.repository.MatchRepository;
import com.web_game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private UserRepository userRepository; // Dùng để cộng điểm

    private Map<String, GameSession> activeGames = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        // Khởi động scheduled tasks
        startTimerCheck();
        startAFKCheck();
    }

    private static final int TURN_TIME_LIMIT_SECONDS = 30; // 30 giây mỗi lượt
    private static final int AFK_TIME_LIMIT_SECONDS = 300; // 5 phút không đánh = AFK

    // 1. JOIN ROOM (Đã sửa để nhận username)
    public String joinRoom(String roomId, String sessionId, String username) {
        GameSession session = activeGames.computeIfAbsent(roomId, k -> new GameSession());

        // Xử lý reconnect (người chơi cũ vào lại)
        if (sessionId.equals(session.playerX_Id) || username.equals(session.playerX_Name)) {
            session.playerX_Id = sessionId; // Cập nhật sessionId mới
            return "X";
        }
        if (sessionId.equals(session.playerO_Id) || username.equals(session.playerO_Name)) {
            session.playerO_Id = sessionId; // Cập nhật sessionId mới
            return "O";
        }

        // Nếu đã có spectator với sessionId này hoặc username này, trả về SPECTATOR
        if (session.spectators.containsKey(sessionId) || session.spectators.containsValue(username)) {
            // Cập nhật sessionId cho spectator (nếu cần tìm kiếm ngược lại thì map cần đảo hoặc loop)
            // Ở đây tạm thời return SPECTATOR luôn
            return "SPECTATOR";
        }
        if (session.playerX_Id == null) {
            session.playerX_Id = sessionId;
            session.playerX_Name = username;
            // Người đầu tiên vào phòng = chủ phòng
            if (session.roomOwnerId == null) {
                session.roomOwnerId = sessionId;
            }
            broadcastLobbyUpdate();
            return "X";
        } else if (session.playerO_Id == null) {
            session.playerO_Id = sessionId;
            session.playerO_Name = username;
            // Bắt đầu tính giờ khi đủ 2 người
            session.turnStartTime = System.currentTimeMillis();
            session.lastMoveTime = System.currentTimeMillis();
            broadcastLobbyUpdate();
            return "O";
        } else {
            // Phòng đầy, cho phép join làm spectator (nếu chưa đạt giới hạn)
            if (session.spectators.size() >= session.maxSpectators) {
                return "FULL";
            }
            session.spectators.put(sessionId, username);
            broadcastLobbyUpdate();
            return "SPECTATOR";
        }
    }

    // Helper wrapper to broadcast update after move
    public synchronized GameState makeMove(String roomId, String senderId, int row, int col) {
        GameState state = makeMoveInternal(roomId, senderId, row, col);
        if (state != null) {
            broadcastLobbyUpdate();
        }
        return state;
    }

    private GameState makeMoveInternal(String roomId, String senderId, int row, int col) {
        GameSession session = activeGames.get(roomId);
        if (session == null)
            return null;

        String currentId = session.currentPlayer.equals("X") ? session.playerX_Id : session.playerO_Id;
        if (currentId == null || !currentId.equals(senderId))
            return null;

        if (session.isGameOver || session.board[row][col] != 0)
            return null;

        session.board[row][col] = (session.currentPlayer.equals("X")) ? 1 : 2;
        session.moveCount++;
        session.lastMoveTime = System.currentTimeMillis();

        WinResult winResult = checkWinWithLine(session.board, row, col);

        GameState state = new GameState();
        state.setRow(row);
        state.setCol(col);
        state.setPlayer(session.currentPlayer);
        state.setMoveNumber(session.moveCount);

        if (winResult.isWin) {
            state.setWinner(session.currentPlayer);
            state.setWinningLine(winResult.winningLine);
            session.isGameOver = true;
            session.winningLine = winResult.winningLine;
            session.turnStartTime = 0; // Reset timer
            // Xử lý kết quả trận đấu (Lưu DB + Tính điểm)
            processGameResult(roomId, session.currentPlayer);
        } else {
            session.currentPlayer = (session.currentPlayer.equals("X")) ? "O" : "X";
            state.setNextTurn(session.currentPlayer);
            // Reset timer cho lượt mới
            session.turnStartTime = System.currentTimeMillis();
            state.setTimeRemaining(TURN_TIME_LIMIT_SECONDS);
        }

        if (winResult.isWin) {
            session.lastWinnerRole = state.getWinner();
        }


        // Lưu lịch sử nước đi
        session.moveHistory.add(new int[] { row, col, session.currentPlayer.equals("X") ? 1 : 2 });

        return state;
    }

    // --- HÀM MỚI: TÍNH ĐIỂM VÀ LƯU LỊCH SỬ ---
    private void processGameResult(String roomId, String winnerRole) {
        GameSession session = activeGames.get(roomId);
        if (session == null)
            return;

        if ("DRAW".equals(winnerRole)) {
            // Xử lý Hòa: Có thể lưu lịch sử nhưng không cộng/trừ điểm
            session.lastWinnerRole = null; // Reset winner cho ván sau
            try {
                MatchHistory match = new MatchHistory();
                match.setRoomId(roomId);
                match.setWinnerUsername("DRAW");
                match.setWinnerRole("NONE");
                match.setLoserUsername("DRAW");
                match.setPlayedAt(LocalDateTime.now());
                matchRepository.save(match);
            } catch (Exception e) { e.printStackTrace(); }
            broadcastLobbyUpdate();
            return;
        }

        String winnerName = winnerRole.equals("X") ? session.playerX_Name : session.playerO_Name;
        String loserName = winnerRole.equals("X") ? session.playerO_Name : session.playerX_Name;

        // 1. Cộng điểm người thắng (+25)
        if (winnerName != null) {
            userRepository.findByUsername(winnerName).ifPresent(u -> {
                u.setScore(u.getScore() + 25);
                u.setWins(u.getWins() + 1);
                userRepository.save(u);
            });
        }

        // 2. Trừ điểm người thua (-10)
        if (loserName != null) {
            userRepository.findByUsername(loserName).ifPresent(u -> {
                u.setScore(Math.max(0, u.getScore() - 10)); // Không trừ âm
                u.setLosses(u.getLosses() + 1);
                userRepository.save(u);
            });
        }

        // 3. Lưu lịch sử đấu
        try {
            MatchHistory match = new MatchHistory();
            match.setRoomId(roomId);
            match.setWinnerUsername(winnerName != null ? winnerName : "Guest");
            match.setWinnerRole(winnerRole);
            match.setLoserUsername(loserName != null ? loserName : "Guest");
            match.setPlayedAt(LocalDateTime.now());
            matchRepository.save(match);
        } catch (Exception e) {
            e.printStackTrace();
        }
        broadcastLobbyUpdate();
    }

    // 3. NEW GAME (Reset bàn cờ, giữ nguyên người chơi)
    public void newGame(String roomId) {
        GameSession oldSession = activeGames.get(roomId);
        if (oldSession != null) {
            GameSession newSession = new GameSession();
            newSession.playerX_Id = oldSession.playerX_Id;
            newSession.playerX_Name = oldSession.playerX_Name;
            newSession.playerO_Id = oldSession.playerO_Id;
            newSession.playerO_Name = oldSession.playerO_Name;
            newSession.roomOwnerId = oldSession.roomOwnerId;
            newSession.maxSpectators = oldSession.maxSpectators;
            newSession.spectators = oldSession.spectators;
            newSession.lastWinnerRole = oldSession.lastWinnerRole;
            
            // Logic: Người thắng ván trước đi trước
            if (oldSession.lastWinnerRole != null && (oldSession.lastWinnerRole.equals("X") || oldSession.lastWinnerRole.equals("O"))) {
                newSession.currentPlayer = oldSession.lastWinnerRole;
            } else {
                newSession.currentPlayer = "X"; // Mặc định ván đầu hoặc hòa
            }
            
            newSession.turnStartTime = System.currentTimeMillis();
            newSession.lastMoveTime = System.currentTimeMillis();
            newSession.lastMoveTime = System.currentTimeMillis();
            activeGames.put(roomId, newSession);
            broadcastLobbyUpdate();

            // Broadcast RESET event to room
            if (messagingTemplate != null) {
                GameState resetState = new GameState();
                resetState.setPlayer("RESET");
                messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, resetState);
            }
        }
    }

    // 7. KICK SPECTATOR (Chủ phòng kick spectator)
    public synchronized boolean kickSpectator(String roomId, String ownerSessionId, String spectatorSessionId) {
        GameSession session = activeGames.get(roomId);
        if (session == null || !session.roomOwnerId.equals(ownerSessionId)) {
            return false; // Không phải chủ phòng
        }
        return session.spectators.remove(spectatorSessionId) != null;
    }

    // Đóng phòng (dùng cho Admin)
    public synchronized void closeRoom(String roomId) {
        activeGames.remove(roomId);
    }

    // Scheduled task: Check timer mỗi lượt
    private void startTimerCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, GameSession> entry : activeGames.entrySet()) {
                String roomId = entry.getKey();
                GameSession session = entry.getValue();

                if (session.isGameOver || session.turnStartTime == 0)
                    continue;
                if (session.playerX_Id == null || session.playerO_Id == null)
                    continue;

                long elapsed = (now - session.turnStartTime) / 1000;
                int remaining = TURN_TIME_LIMIT_SECONDS - (int) elapsed;

                // Gửi time remaining cho client
                if (messagingTemplate != null && remaining >= 0) {
                    GameState timeState = new GameState();
                    timeState.setTimeRemaining(remaining);
                    messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, timeState);
                }

                // Hết giờ -> auto thua
                if (remaining <= 0) {
                    synchronized (session) {
                        if (session.isGameOver || session.turnStartTime == 0)
                            continue;
                        String timeoutRole = session.currentPlayer;
                        String winnerRole = timeoutRole.equals("X") ? "O" : "X";

                        session.isGameOver = true;
                        session.turnStartTime = 0;

                        GameState state = new GameState();
                        state.setWinner(winnerRole);
                        state.setPlayer(winnerRole);
                        state.setEndReason("TIME_OUT");

                        processGameResult(roomId, winnerRole);
                        if (messagingTemplate != null) {
                            messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, state);
                        }
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS); // Check mỗi giây
    }

    // Scheduled task: Check AFK
    private void startAFKCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, GameSession> entry : activeGames.entrySet()) {
                String roomId = entry.getKey();
                GameSession session = entry.getValue();

                if (session.isGameOver)
                    continue;
                if (session.playerX_Id == null || session.playerO_Id == null)
                    continue;
                if (session.lastMoveTime == 0)
                    continue;

                long afkTime = (now - session.lastMoveTime) / 1000;

                // Nếu cả 2 người đều AFK quá lâu -> không xử lý
                // Chỉ xử lý khi 1 người AFK và đang đến lượt họ
                if (afkTime >= AFK_TIME_LIMIT_SECONDS) {
                    synchronized (session) {
                        if (session.isGameOver)
                            continue;
                        // Xử lý AFK: người đang đến lượt bị thua
                        String afkRole = session.currentPlayer;
                        String winnerRole = afkRole.equals("X") ? "O" : "X";

                        session.isGameOver = true;
                        session.turnStartTime = 0;
                        session.lastMoveTime = 0;

                        GameState state = new GameState();
                        state.setWinner(winnerRole);
                        state.setPlayer(winnerRole);
                        state.setEndReason("AFK");

                        processGameResult(roomId, winnerRole);
                        if (messagingTemplate != null) {
                            messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, state);
                        }
                    }
                }

            }
        }, 10, 10, TimeUnit.SECONDS); // Check mỗi 10 giây
    }

    // --- HÀM MỚI: Lấy trọn bộ GameState hiện tại (để sync cho người mới vào) ---
    public GameState getGameState(String roomId) {
        GameSession session = activeGames.get(roomId);
        if (session == null)
            return null;

        GameState state = new GameState();
        state.setPlayer(session.currentPlayer);
        state.setNextTurn(session.currentPlayer);
        state.setGameOver(session.isGameOver);
        if (session.isGameOver) {
            state.setWinner(session.lastWinnerRole);
            state.setWinningLine(session.winningLine);
        }
        state.setMoveHistory(new java.util.ArrayList<>(session.moveHistory));
        state.setMoveNumber(session.moveCount);
        
        // Tính thời gian còn lại
        if (session.turnStartTime > 0 && !session.isGameOver) {
            long elapsed = (System.currentTimeMillis() - session.turnStartTime) / 1000;
            state.setTimeRemaining(Math.max(0, TURN_TIME_LIMIT_SECONDS - (int) elapsed));
        }
        
        return state;
    }

    // 4. GET MOVE HISTORY
    public java.util.List<int[]> getMoveHistory(String roomId) {
        GameSession session = activeGames.get(roomId);
        if (session == null)
            return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(session.moveHistory);
    }

    // 5. SURRENDER (Đầu hàng)
    public synchronized GameState surrender(String roomId, String senderId) {
        GameSession session = activeGames.get(roomId);
        if (session == null || session.isGameOver)
            return null;

        String winnerRole = senderId.equals(session.playerX_Id) ? "O" : "X";
        session.isGameOver = true;

        GameState state = new GameState();
        state.setWinner(winnerRole);
        state.setPlayer(winnerRole);
        state.setEndReason("SURRENDER");

        processGameResult(roomId, winnerRole);
        broadcastLobbyUpdate();
        return state;
    }

    // 6. REQUEST REMATCH
    public synchronized boolean requestRematch(String roomId, String sessionId, String username) {
        GameSession session = activeGames.get(roomId);
        if (session == null || !session.isGameOver)
            return false;

        session.rematchRequests.put(username, true);

        // Kiểm tra cả 2 người chơi đều ready
        boolean bothReady = session.playerX_Name != null && session.playerO_Name != null
                && session.rematchRequests.getOrDefault(session.playerX_Name, false)
                && session.rematchRequests.getOrDefault(session.playerO_Name, false);

        if (bothReady) {
            newGame(roomId);
            session.rematchRequests.clear();
            return true;
        }
        return false;
    }

    /**
     * Xử lý khi một WebSocket session bị ngắt kết nối.
     * Nếu đang chơi và một trong hai người chơi out phòng -> xử thua cho người đó,
     * thắng cho đối thủ.
     */
    public synchronized DisconnectResult handleDisconnect(String sessionId) {
        for (Map.Entry<String, GameSession> entry : activeGames.entrySet()) {
            String roomId = entry.getKey();
            GameSession session = entry.getValue();

            // Nếu là spectator -> chỉ xoá khỏi danh sách, không xử lý thắng thua
            if (session.spectators.remove(sessionId) != null) {
                continue;
            }

            boolean isPlayerX = sessionId.equals(session.playerX_Id);
            boolean isPlayerO = sessionId.equals(session.playerO_Id);

            if (!isPlayerX && !isPlayerO)
                continue;

            // Nếu ván đã kết thúc, chỉ giải phóng slot, không tính thêm kết quả
            if (session.isGameOver) {
                if (isPlayerX) {
                    session.playerX_Id = null;
                    session.playerX_Name = null;
                } else {
                    session.playerO_Id = null;
                    session.playerO_Name = null;
                }

                if (session.playerX_Id == null && session.playerO_Id == null) {
                    activeGames.remove(roomId);
                }
                return null;
            }

            // Nếu ván chưa bắt đầu (chỉ có 1 người chơi) -> chỉ giải phóng slot
            if ((session.playerX_Id == null || session.playerO_Id == null)) {
                if (isPlayerX) {
                    session.playerX_Id = null;
                    session.playerX_Name = null;
                } else {
                    session.playerO_Id = null;
                    session.playerO_Name = null;
                }

                if (session.playerX_Id == null && session.playerO_Id == null) {
                    activeGames.remove(roomId);
                }
                return null;
            }

            // Đang chơi và một bên out -> bên còn lại thắng
            String leaverRole = isPlayerX ? "X" : "O";
            String winnerRole = leaverRole.equals("X") ? "O" : "X";

            // Giải phóng người rời phòng
            if (isPlayerX) {
                session.playerX_Id = null;
                session.playerX_Name = null;
            } else {
                session.playerO_Id = null;
                session.playerO_Name = null;
            }

            session.isGameOver = true;

            GameState state = new GameState();
            state.setWinner(winnerRole);
            state.setPlayer(winnerRole);
            state.setEndReason("OPPONENT_LEFT");

            processGameResult(roomId, winnerRole);

            // Nếu cả 2 người đều không còn trong phòng -> đặt lịch xóa phòng sau 30s 
            // Điều này giúp phòng không bị mất ngay khi người chơi F5 trang
            if (session.playerX_Id == null && session.playerO_Id == null) {
                scheduleRoomCleanup(roomId);
            }

            broadcastLobbyUpdate();
            return new DisconnectResult(roomId, state);
        }
        return null;
    }

    private void scheduleRoomCleanup(String roomId) {
        scheduler.schedule(() -> {
            GameSession session = activeGames.get(roomId);
            if (session != null && session.playerX_Id == null && session.playerO_Id == null) {
                activeGames.remove(roomId);
                broadcastLobbyUpdate();
                System.out.println("Cleaned up empty room: " + roomId);
            }
        }, 30, TimeUnit.SECONDS); 
    }

    // Check thắng thua và trả về đường thắng
    private WinResult checkWinWithLine(int[][] board, int r, int c) {
        int player = board[r][c];
        int[] dRow = { 0, 1, 1, 1 };
        int[] dCol = { 1, 0, 1, -1 };

        for (int i = 0; i < 4; i++) {
            java.util.List<int[]> line = new java.util.ArrayList<>();
            line.add(new int[] { r, c });

            int countForward = countDirectionWithLine(board, r, c, dRow[i], dCol[i], player, line);
            int countBackward = countDirectionWithLine(board, r, c, -dRow[i], -dCol[i], player, line);

            if (countForward + countBackward + 1 >= 5) {
                // Sắp xếp lại line để đúng thứ tự
                line.sort((a, b) -> {
                    if (a[0] != b[0])
                        return Integer.compare(a[0], b[0]);
                    return Integer.compare(a[1], b[1]);
                });
                return new WinResult(true, line);
            }
        }
        return new WinResult(false, null);
    }

    private int countDirectionWithLine(int[][] board, int r, int c, int dr, int dc, int player,
            java.util.List<int[]> line) {
        int count = 0;
        for (int k = 1; k < 5; k++) {
            int nr = r + k * dr;
            int nc = c + k * dc;
            if (nr < 0 || nr >= 15 || nc < 0 || nc >= 15 || board[nr][nc] != player)
                break;
            line.add(new int[] { nr, nc });
            count++;
        }
        return count;
    }

    // 6. DRAW (Cầu hòa)
    // 6. DRAW (Cầu hòa)
    public void processDrawRequest(String roomId, String senderId) {
        System.out.println("Processing draw request for room: " + roomId + ", sender: " + senderId);
        GameSession session = activeGames.get(roomId);
        if (session == null || session.isGameOver) {
            System.out.println("Session invalid or game over for room: " + roomId);
            return;
        }

        // Xác định đối thủ
        String opponentId = null;
        if (senderId.equals(session.playerX_Id)) {
            opponentId = session.playerO_Id;
        } else if (senderId.equals(session.playerO_Id)) {
            opponentId = session.playerX_Id;
        }

        System.out.println("Opponent ID identified: " + opponentId);

        if (opponentId != null && messagingTemplate != null) {
            // Gửi yêu cầu cho đối thủ
            System.out.println("Sending draw request to topic: /topic/draw-request/" + opponentId);
            messagingTemplate.convertAndSend("/topic/draw-request/" + opponentId,
                    new com.web_game.dto.DrawRequestDTO(roomId, senderId));
        } else {
            System.out.println(
                    "Cannot send draw request. OpponentId: " + opponentId + ", Template: " + messagingTemplate);
        }
    }

    public void processDrawResponse(String roomId, String senderId, boolean accepted) {
        GameSession session = activeGames.get(roomId);
        if (session == null || session.isGameOver)
            return;

        if (accepted) {
            session.isGameOver = true;
            GameState state = new GameState();
            state.setWinner(null);
            state.setPlayer("DRAW");
            state.setEndReason("DRAW");

            processGameResult(roomId, "DRAW");
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/game-progress/" + roomId, state);
            }
        } else {
            // Thông báo từ chối (Optional)
            String opponentId = senderId.equals(session.playerX_Id) ? session.playerO_Id : session.playerX_Id;
            if (opponentId != null && messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/draw-response/" + opponentId, "REJECTED");
            }
        }
    }

    // Class helper để trả về kết quả checkWin
    private static class WinResult {
        boolean isWin;
        java.util.List<int[]> winningLine;

        WinResult(boolean isWin, java.util.List<int[]> winningLine) {
            this.isWin = isWin;
            this.winningLine = winningLine;
        }
    }

    // Kết quả khi xử lý disconnect
    public static class DisconnectResult {
        private final String roomId;
        private final GameState state;

        public DisconnectResult(String roomId, GameState state) {
            this.roomId = roomId;
            this.state = state;
        }

        public String getRoomId() {
            return roomId;
        }

        public GameState getState() {
            return state;
        }
    }

    // Lấy thông tin tất cả phòng (dùng cho RoomService)
    public Map<String, GameSession> getAllActiveGames() {
        return new ConcurrentHashMap<>(activeGames);
    }

    // Class lưu trạng thái phòng chơi
    static class GameSession {
        int[][] board = new int[15][15];
        String currentPlayer = "X";
        boolean isGameOver = false;
        String playerX_Id = null;
        String playerX_Name = null;
        String playerO_Id = null;
        String playerO_Name = null;
        int moveCount = 0;
        java.util.List<int[]> moveHistory = new java.util.ArrayList<>(); // [row, col, player]
        java.util.List<int[]> winningLine = null;
        java.util.Map<String, String> spectators = new java.util.concurrent.ConcurrentHashMap<>(); // sessionId ->
                                                                                                   // username
        java.util.Map<String, Boolean> rematchRequests = new java.util.concurrent.ConcurrentHashMap<>(); // username ->
                                                                                                         // ready
        long turnStartTime = 0; // Thời gian bắt đầu lượt hiện tại (millis)
        long lastMoveTime = 0; // Thời gian nước đi cuối cùng (millis)
        String roomOwnerId = null; // SessionId của chủ phòng (người tạo phòng đầu tiên)
        int maxSpectators = 10; // Giới hạn số spectator
        String lastWinnerRole = null; // Vai thắng ván trước
    }

    // --- BROADCAST LOBBY UPDATE ---
    private void broadcastLobbyUpdate() {
        if (messagingTemplate == null)
            return;

        java.util.List<com.web_game.dto.RoomDTO> rooms = new java.util.ArrayList<>();
        for (Map.Entry<String, GameSession> entry : activeGames.entrySet()) {
            String roomId = entry.getKey();
            GameSession session = entry.getValue();

            com.web_game.dto.RoomDTO dto = new com.web_game.dto.RoomDTO();
            dto.setRoomId(roomId);
            dto.setPlayerXName(session.playerX_Name != null ? session.playerX_Name : "");
            dto.setPlayerOName(session.playerO_Name != null ? session.playerO_Name : "");
            dto.setFull(session.playerX_Name != null && session.playerO_Name != null);
            dto.setGameOver(session.isGameOver);
            dto.setCurrentPlayer(session.currentPlayer);

            int moveCount = 0;
            for (int[] row : session.board) {
                for (int cell : row) {
                    if (cell != 0)
                        moveCount++;
                }
            }
            dto.setMoveCount(moveCount);
            rooms.add(dto);
        }

        messagingTemplate.convertAndSend("/topic/lobby", rooms);
    }

}