package com.web_game.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    // Đây là "chìa khóa bí mật" để ký tên lên thẻ. KHÔNG ĐƯỢC LỘ RA NGOÀI.
    @org.springframework.beans.factory.annotation.Value("${app.jwtSecret}")
    private String jwtSecret;

    @org.springframework.beans.factory.annotation.Value("${app.jwtExpirationMs}")
    private int jwtExpirationMs;

    // Lấy key từ chuỗi bí mật
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes()));
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 1. TẠO TOKEN TỪ USERNAME
    public String generateJwtToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. LẤY USERNAME TỪ TOKEN
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // 3. KIỂM TRA TOKEN CÓ HỢP LỆ KHÔNG
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException e) {
            System.err.println("Invalid JWT token: " + e.getMessage());
        }
        return false;
    }
}