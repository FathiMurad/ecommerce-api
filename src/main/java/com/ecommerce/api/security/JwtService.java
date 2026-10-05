package com.ecommerce.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for generating, signing, parsing, and validating JSON Web Tokens (JWT).
 */
@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Generates a signed JWT token containing standard claims for the given user.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a signed JWT token containing extra custom claims and subject.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long currentTimeMillis = System.currentTimeMillis();

        return Jwts.builder().claims(extraClaims).subject(userDetails.getUsername()).issuedAt(new Date(currentTimeMillis)).expiration(new Date(currentTimeMillis + jwtExpiration)).signWith(getSigningKey(), Jwts.SIG.HS256).compact();
    }

    /**
     * Extracts the subject (user email) from the token payload.
     */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    /**
     * Validates whether the token matches the user and is not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Claims claims = extractAllClaims(token);
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
