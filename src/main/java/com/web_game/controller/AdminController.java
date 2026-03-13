package com.web_game.controller;

import com.web_game.dto.RoomDTO;
import com.web_game.model.User;
import com.web_game.repository.UserRepository;
import com.web_game.service.GameService;
import com.web_game.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameService gameService;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdminUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) return false;
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        return user != null && user.isAdmin();
    }

    @GetMapping("/rooms")
    public ResponseEntity<?> getAllRoomsAdmin() {
        if (!isAdminUser()) return ResponseEntity.status(403).body("Forbidden");
        List<RoomDTO> rooms = roomService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<?> closeRoom(@PathVariable String roomId) {
        if (!isAdminUser()) return ResponseEntity.status(403).body("Forbidden");
        gameService.closeRoom(roomId);
        return ResponseEntity.ok("Đã đóng phòng " + roomId);
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        if (!isAdminUser()) return ResponseEntity.status(403).body("Forbidden");
        Map<String, Object> resp = new HashMap<>();
        resp.put("roomCount", roomService.getAllRooms().size());
        resp.put("userCount", userRepository.count());
        return ResponseEntity.ok(resp);
    }
}


