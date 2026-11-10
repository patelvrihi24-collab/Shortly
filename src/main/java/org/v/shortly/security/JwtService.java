package org.v.shortly.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;            // the signing key, derived from the secret
    private final Duration expiration;      // how long a token stays valid

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // text -> bytes -> HMAC key. Throws if shorter than 32 bytes, so the app fails at startup (good).
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))                         // "sub": who this token is about
                .claim("email", email)                                   // a custom field in the payload
                .issuedAt(now)                                           // "iat": when it was created
                .expiration(new Date(now.getTime() + expiration.toMillis()))  // "exp": when it dies
                .signWith(key)                                           // sign with HMAC-SHA256: tamper-proof
                .compact();                                              // build the final "xxx.yyy.zzz" string
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)                                         // check the signature with our key
                .build()
                .parseSignedClaims(token)                                // throws if forged, malformed or EXPIRED
                .getPayload();                                           // the claims inside
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class));
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }
}