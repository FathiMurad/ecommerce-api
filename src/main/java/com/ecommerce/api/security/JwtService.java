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
 * Service responsible for generating, signing, parsing, and validating JSON Web Tokens (JWT).
 * Utilizes cached cryptographic keys and single-pass claim resolution for enterprise performance.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    private volatile SecretKey cachedSigningKey;

    /**
     * Generates a signed JWT token containing standard claims for the given user.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a signed JWT token containing custom claims and user subject.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long currentTimeMillis = System.currentTimeMillis();

        return Jwts.builder().claims(extraClaims).subject(userDetails.getUsername()).issuedAt(new Date(currentTimeMillis)).expiration(new Date(currentTimeMillis + jwtExpiration)).signWith(getSigningKey(), Jwts.SIG.HS256).compact();
    }

    /**
     * Extracts the subject (username/email) from the token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from the token payload using a resolver function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Resiliently validates token claims against user details in a single cryptographic pass.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final Claims claims = extractAllClaims(token);
            final String username = claims.getSubject();
            final Date expiration = claims.getExpiration();

            return username != null && userDetails != null && username.equalsIgnoreCase(userDetails.getUsername().trim()) && expiration != null && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT verification failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Extracts all payload claims from a signed JWT.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Returns the cached HMAC-SHA signing key, decoding it lazily thread-safely once.
     */
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
