package com.web_game.repository;

import com.web_game.model.MatchHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MatchRepository extends MongoRepository<MatchHistory, String> {
    List<MatchHistory> findByWinnerUsernameOrLoserUsernameOrderByPlayedAtDesc(String winnerUsername, String loserUsername, Pageable pageable);
    List<MatchHistory> findByRoomIdOrderByPlayedAtDesc(String roomId);
}