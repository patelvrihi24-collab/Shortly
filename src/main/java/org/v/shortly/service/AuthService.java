package org.v.shortly.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.v.shortly.dto.AuthResponse;
import org.v.shortly.dto.LoginRequest;
import org.v.shortly.dto.RegisterRequest;
import org.v.shortly.entity.User;
import org.v.shortly.exception.EmailAlreadyExistsException;
import org.v.shortly.exception.InvalidCredentialsException;
import org.v.shortly.repository.UserRepository;
import org.v.shortly.security.JwtService;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;      // a real hash of a throwaway password, used in login (see below)

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());                   // "Me@X.com " and "me@x.com" are the same person
        if (userRepository.existsByEmail(email)) {                   // friendly early check
            throw new EmailAlreadyExistsException("Email already registered");
        }
        User user;
        try {
            user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
            // We store only the hash. BCrypt adds a random salt, so the same password hashes differently each time.
        } catch (DataIntegrityViolationException e) {                // two simultaneous sign-ups: the UNIQUE constraint decides
            throw new EmailAlreadyExistsException("Email already registered");
        }
        return tokenFor(user);                                       // users are logged in right after registering
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        String hash = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        // We ALWAYS run a BCrypt check, even for unknown emails. BCrypt is slow (~100 ms), so without this an
        // attacker could tell "email exists" from "email unknown" just by measuring response time (a timing attack).
        if (user == null || !passwordOk) {
            throw new InvalidCredentialsException("Invalid email or password");
            // One message for both cases, so attackers can't use login to find out which emails are registered.
        }
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail()),
                "Bearer", jwtService.expiresInSeconds());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);                // Locale.ROOT avoids locale bugs (e.g. the Turkish "I")
    }
}