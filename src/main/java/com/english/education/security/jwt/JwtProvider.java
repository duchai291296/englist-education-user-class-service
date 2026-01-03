package com.english.education.security.jwt;


import com.english.education.model.enums.RoleName;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expired;

    public String generateToken(String username, Long tokenVer, Integer userId, String device, Set<RoleName> userRole) {

        List<String> roles = userRole.stream()
                .map(Enum::name)
                .toList();

        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("device", device)
                .claim("tokenVer",tokenVer)
                .claim("userRole",roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + expired))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Date getExpirationDateFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.error("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims empty or invalid: {}", e.getMessage());
        }
        return false;
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(secretKey)
        );
    }

    public Integer getUserIdFromToken(String token) {
        Claims claims = parse(token);
        return claims.get("userId", Integer.class);
    }
}
