package com.ecommerce.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates, signs, parses, and validates HMAC-SHA256 JSON Web Tokens (JWT).
 */
@Slf4j
@Service
public class JwtService {

    @Value("${app.security.jwt.secret-key}")
    private String secretKey;

    @Value("${app.security.jwt.expiration}")
    private long jwtExpiration;

    private volatile SecretKey cachedSigningKey;

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long currentTimeMillis = System.currentTimeMillis();
        Date issuedAt = new Date(currentTimeMillis);
        Date expiration = new Date(currentTimeMillis + jwtExpiration);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }

        try {
            // Single cryptographic pass to verify signature and inspect all required claims
            final Claims claims = extractAllClaims(token);
            final String username = claims.getSubject();
            final Date expiration = claims.getExpiration();

            boolean isUsernameMatching = username != null
                    && username.equalsIgnoreCase(userDetails.getUsername().trim());
            boolean isNotExpired = expiration != null && expiration.after(new Date());

            return isUsernameMatching && isNotExpired;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT verification failed: {}", ex.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        if (cachedSigningKey == null) {
            synchronized (this) {
                if (cachedSigningKey == null) {
                    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
                    cachedSigningKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
        }
        return cachedSigningKey;
    }
}
